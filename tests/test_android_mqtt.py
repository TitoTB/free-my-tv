"""Real production Java/Paho transport against a local AMQTT broker; never uses the household broker."""
import asyncio
import json
import os
from pathlib import Path
import queue
import socket
import subprocess
import threading
import time
from contextlib import contextmanager

import pytest
import paho.mqtt.client as mqtt
from amqtt.broker import Broker

ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture
def broker():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    loop = asyncio.new_event_loop()
    thread = threading.Thread(target=loop.run_forever, daemon=True)
    thread.start()

    async def start():
        obj = Broker({'listeners': {'default': {'type': 'tcp', 'bind': f'127.0.0.1:{port}'}},
                      'plugins': {'amqtt.plugins.authentication.AnonymousAuthPlugin': {'allow_anonymous': True}}})
        await obj.start()
        return obj

    obj = asyncio.run_coroutine_threadsafe(start(), loop).result(10)
    class BrokerPort(int):
        def restart(self):
            nonlocal obj
            asyncio.run_coroutine_threadsafe(obj.shutdown(), loop).result(10)
            obj = asyncio.run_coroutine_threadsafe(start(), loop).result(10)
    try:
        yield BrokerPort(port)
    finally:
        asyncio.run_coroutine_threadsafe(obj.shutdown(), loop).result(10)
        loop.call_soon_threadsafe(loop.stop)
        thread.join(5)
        loop.close()


class Monitor:
    def __init__(self, port):
        self.messages = []
        self.ready = threading.Event()
        self.condition = threading.Condition()
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, clean_session=True)
        self.client.on_connect = lambda c, u, f, r, p: c.subscribe('#', qos=0)
        self.client.on_subscribe = lambda *args: self.ready.set()
        self.client.on_message = self.received
        self.client.connect('127.0.0.1', int(port), 15)
        self.client.loop_start()
        assert self.ready.wait(5)

    def received(self, c, u, message):
        with self.condition:
            self.messages.append(message)
            self.condition.notify_all()

    def wait(self, predicate, after=0, timeout=6):
        deadline = time.monotonic() + timeout
        with self.condition:
            while True:
                found = [m for m in self.messages[after:] if predicate(m)]
                if found:
                    return found[0]
                remaining = deadline-time.monotonic()
                if remaining <= 0:
                    raise AssertionError('Expected MQTT publication was not received')
                self.condition.wait(remaining)

    def publish(self, topic, payload, retain=False, qos=0):
        self.client.publish(topic, payload, qos=qos, retain=retain).wait_for_publish(5)

    def close(self):
        self.client.disconnect()
        self.client.loop_stop()


@contextmanager
def monitor(port):
    m = Monitor(port)
    try:
        yield m
    finally:
        m.close()


@contextmanager
def tv(port, identity='living-room'):
    java = os.environ.get('JAVA', str(ROOT/'.tools/jdk/jdk-17.0.20.1+1/bin/java.exe'))
    repo = Path(__file__).resolve().parents[1]
    classes = os.environ.get('ANDROID_TV_TEST_CLASSES', str(ROOT/'.tools/source-check/classes'))
    cp = os.pathsep.join([classes, str(ROOT/'.tools/json-tests.jar'),
                         str(repo/'app/libs/org.eclipse.paho.client.mqttv3-1.2.5.jar')])
    proc = subprocess.Popen([java, '-Dfile.encoding=UTF-8', '-cp', cp, 'io.freemytv.AndroidMqttHarness', str(port), identity],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, encoding='utf-8')
    lines = queue.Queue()
    def read():
        for line in proc.stdout:
            lines.put(json.loads(line))
    threading.Thread(target=read, daemon=True).start()
    try:
        assert lines.get(timeout=8) == {'connected': True}
        yield proc, lines
    finally:
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:
                proc.wait(8)
            except subprocess.TimeoutExpired:
                proc.kill();proc.wait(5)
        assert proc.returncode in (0, 1) or proc.returncode < 0


def test_discovery_groups_entities_and_reannounces_after_ha_restart(broker):
    with monitor(broker) as m, tv(broker):
        m.wait(lambda p: p.topic.endswith('/availability') and p.payload == b'online')
        configs = [json.loads(p.payload) for p in m.messages if p.topic.startswith('homeassistant/')]
        assert len(configs) == 10
        assert len({c['device']['identifiers'][0] for c in configs}) == 1
        before = len(m.messages)
        m.publish('homeassistant/status', 'online')
        m.wait(lambda p: p.topic.endswith('/availability') and p.payload == b'online', after=before)
        assert len([p for p in m.messages[before:] if p.topic.startswith('homeassistant/') and p.topic.endswith('/config')]) == 10


def test_native_notify_and_rich_persistent_command(broker):
    with monitor(broker) as m, tv(broker) as (_, lines):
        m.publish('android_tv/living-room/notify/normal', '{Mensaje literal} 🏠')
        normal = lines.get(timeout=5)
        assert normal['message'] == '{Mensaje literal} 🏠' and normal['type'] == 'normal'
        m.publish('android_tv/living-room/notify/critical', 'Alerta')
        critical = lines.get(timeout=5)
        assert critical['type'] == 'critical' and critical['persistent'] is True
        m.publish('android_tv/living-room/notify/set', json.dumps({'message': 'Critical override', 'persistent': False,
                  'notification_type': 'critical'}))
        assert lines.get(timeout=5)['persistent'] is True
        m.publish('android_tv/living-room/notify/set', json.dumps({'message': 'Puerta', 'persistent': True,
                  'notification_type': 'critical', 'notification_id': 'door'}))
        rich = lines.get(timeout=5)
        assert rich['persistent'] and rich['received'] == 'door' and rich['type'] == 'critical'


def test_retained_commands_are_ignored_on_subscription(broker):
    with monitor(broker) as m:
        m.publish('android_tv/living-room/notify/normal', 'NO REPLAY', retain=True)
        with tv(broker) as (_, lines):
            with pytest.raises(queue.Empty):
                lines.get(timeout=.7)
            m.publish('android_tv/living-room/notify/normal', 'Live')
            assert lines.get(timeout=5)['message'] == 'Live'


def test_offline_send_is_not_replayed_in_clean_session(broker):
    with monitor(broker) as m:
        with tv(broker):
            pass
        m.publish('android_tv/living-room/notify/normal', 'Offline', qos=1)
        with tv(broker) as (_, lines):
            with pytest.raises(queue.Empty):
                lines.get(timeout=.7)
            m.publish('android_tv/living-room/notify/normal', 'New')
            assert lines.get(timeout=5)['message'] == 'New'


def test_two_tvs_are_isolated(broker):
    with monitor(broker) as m, tv(broker, 'first') as (_, a), tv(broker, 'second') as (_, b):
        m.publish('android_tv/first/notify/normal', 'First only')
        assert a.get(timeout=5)['message'] == 'First only'
        with pytest.raises(queue.Empty):
            b.get(timeout=.4)
        m.publish('android_tv/second/notify/normal', 'Second only')
        assert b.get(timeout=5)['message'] == 'Second only'


def test_event_is_not_retained_but_discovery_and_state_are(broker):
    with monitor(broker) as m, tv(broker) as (proc, _):
        proc.stdin.write('event\n');proc.stdin.flush()
        event = m.wait(lambda p: p.topic.endswith('/event'))
        assert json.loads(event.payload)['event_type'] == 'acknowledged'
        with monitor(broker) as fresh:
            state = fresh.wait(lambda p: p.topic.endswith('/state'))
            assert state.retain
            fresh.wait(lambda p: p.topic.startswith('homeassistant/') and p.retain)
            with pytest.raises(AssertionError):
                fresh.wait(lambda p: p.topic.endswith('/event'), timeout=.5)


def test_last_will_marks_connection_offline(broker):
    with monitor(broker) as m, tv(broker) as (proc, _):
        m.wait(lambda p: p.topic.endswith('/availability') and p.payload == b'online')
        proc.kill();proc.wait(5)
        offline = m.wait(lambda p: p.topic.endswith('/availability') and p.payload == b'offline')
        assert offline.qos == 0


def test_oversize_and_invalid_commands_do_not_break_following_messages(broker):
    with monitor(broker) as m, tv(broker) as (_, lines):
        m.publish('android_tv/living-room/notify/normal', 'x'*5000)
        with pytest.raises(queue.Empty):
            lines.get(timeout=.4)
        m.publish('android_tv/living-room/notify/set', 'invalid JSON')
        assert lines.get(timeout=5) == {'invalid': True}
        m.publish('android_tv/living-room/notify/normal', 'Still connected')
        assert lines.get(timeout=5)['message'] == 'Still connected'


def test_reconnect_announces_again_without_replaying_offline_notifications(broker):
    with tv(broker) as (_, lines):
        broker.restart()
        with monitor(broker) as m:
            m.publish('android_tv/living-room/notify/normal', 'Do not replay', qos=1)
            assert lines.get(timeout=8) == {'connected': True}
            m.publish('android_tv/living-room/notify/normal', 'After reconnect')
            assert lines.get(timeout=5)['message'] == 'After reconnect'
