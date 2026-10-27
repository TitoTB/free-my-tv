package io.freemytv;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Outbound MQTT 3.1.1. Clean sessions, bounded volatile outbox, no offline commands or retries. */
final class AndroidMqtt implements AutoCloseable {
    interface Receiver { void message(String topic,String payload,boolean retained); }
    static final class Publication {
        final String topic,payload; final boolean retain;
        Publication(String topic,String payload,boolean retain) { this.topic=topic;this.payload=payload;this.retain=retain; }
    }
    private final ArrayBlockingQueue<Publication> outbox=new ArrayBlockingQueue<>(64);
    private final String uri,user,password,id,base;
    private final Receiver receiver;
    private final Consumer<AndroidMqtt> announce;
    private volatile boolean stopped,ready,rediscover;
    private volatile MqttClient client;
    private final Thread worker;
    AndroidMqtt(String uri,String user,String password,String id,Receiver receiver,Consumer<AndroidMqtt> announce) {
        this.uri=uri;this.user=user;this.password=password;this.id=id;this.base="android_tv/"+id;
        this.receiver=receiver;this.announce=announce;
        worker=new Thread(this::run,"android-mqtt");
    }
    void start() { worker.start(); }
    boolean connected() { return ready; }
    boolean publish(String topic,String payload,boolean retain) {
        if(!ready || stopped) return false;
        boolean accepted=outbox.offer(new Publication(topic,payload,retain));
        if(!accepted) {
            // Make room before announcing again: resync must not keep refilling an already full queue.
            outbox.clear();rediscover=true;
        }
        return accepted;
    }
    private void send(MqttClient c,Publication p) throws MqttException {
        c.publish(p.topic,p.payload.getBytes(StandardCharsets.UTF_8),0,p.retain);
    }
    private void run() {
        long delay=1000;
        while(!stopped) {
            MqttClient c=null;
            try {
                c=new MqttClient(uri,"android_"+id.replace("-",""),new MemoryPersistence()); client=c;
                c.setTimeToWait(5000);
                c.setCallback(new MqttCallback() {
                    public void connectionLost(Throwable cause) { ready=false;outbox.clear(); }
                    public void deliveryComplete(IMqttDeliveryToken token) { }
                    public void messageArrived(String topic,MqttMessage message) {
                        if(message.getPayload().length>4096) return;
                        String payload=new String(message.getPayload(),StandardCharsets.UTF_8);
                        if("homeassistant/status".equals(topic)) {
                            if("online".equals(payload)) rediscover=true;
                        } else if(ready && !message.isRetained()) receiver.message(topic,payload,false);
                    }
                });
                MqttConnectOptions options=new MqttConnectOptions();
                options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                options.setCleanSession(true);options.setAutomaticReconnect(false);
                options.setConnectionTimeout(5);options.setKeepAliveInterval(15);
                options.setHttpsHostnameVerificationEnabled(true);
                if(!user.isEmpty()) { options.setUserName(user);options.setPassword(password.toCharArray()); }
                options.setWill(base+"/availability","offline".getBytes(StandardCharsets.UTF_8),0,true);
                c.connect(options);
                // Commands received before this fresh subscription cannot be queued by the broker.
                ready=true;
                c.subscribe(new String[]{base+"/notify/normal",base+"/notify/critical",base+"/notify/set","homeassistant/status"},new int[]{0,0,0,0});
                rediscover=true; delay=1000;
                while(!stopped && c.isConnected()) {
                    if(rediscover) { rediscover=false;announce.accept(this); }
                    Publication p=outbox.poll(500,TimeUnit.MILLISECONDS);
                    if(p!=null) send(c,p);
                }
                if(stopped && c.isConnected()) {
                    send(c,new Publication(base+"/availability","offline",true));
                    c.disconnect(1000);
                }
            } catch(Exception ignored) { /* Never expose credentials, payloads or raw broker errors. */ }
            finally {
                ready=false;outbox.clear();client=null;
                if(c!=null) {
                    try { if(c.isConnected()) c.disconnectForcibly(0,1000,false); } catch(Exception ignored) { }
                    try { c.close(true); } catch(Exception ignored) { }
                }
            }
            if(!stopped) try { Thread.sleep(delay);delay=Math.min(30000,delay*2); } catch(InterruptedException ignored) { }
        }
    }
    public void close() { stopped=true;ready=false;outbox.clear();worker.interrupt(); }
}
