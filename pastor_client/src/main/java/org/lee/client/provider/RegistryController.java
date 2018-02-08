package org.lee.client.provider;

import org.lee.common.exception.remoting.RemotingException;
import org.lee.common.serialization.SerializerHolder;
import org.lee.common.transport.body.AckCustomBody;
import org.lee.common.util.SystemClock;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author liqiang
 * @description
 * @time 2018年02月08日
 * @modifytime
 */
public class RegistryController {
    private static final Logger logger = LoggerFactory.getLogger(RegistryController.class);

    private DefaultProvider defaultProvider;

    private final ConcurrentMap<Long, MessageNonAck> messagesNonAcks = new ConcurrentHashMap<Long, MessageNonAck>();


    public RegistryController(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void publishedAndStartProvider() throws InterruptedException, RemotingException {
        List<RemotingTransporter> remotingTransporters = defaultProvider.getPublishRemotingTransporters();
        if (remotingTransporters == null || remotingTransporters.size() == 0) {
            logger.warn("service is empty please call DefaultProvider #publishService method");
            return;
        }

        String address = defaultProvider.getRegistryAddress();
        if (address == null) {
            logger.warn("registry center address is empty please check your address");
            return;
        }
        String[] addresses = address.split(",");
        if (null != addresses && addresses.length > 0) {
            for (String registryAddress : addresses) {
                for (RemotingTransporter transporter : remotingTransporters) {
                    pushPublishServiceToRegistry(transporter, registryAddress);
                }
            }
        }
    }

    private void pushPublishServiceToRegistry(RemotingTransporter transporter, String registryAddress) throws InterruptedException, RemotingException {
        MessageNonAck messagesNonAck = new MessageNonAck(transporter, registryAddress);
        messagesNonAcks.put(messagesNonAck.getId(), messagesNonAck);
        RemotingTransporter response = defaultProvider.getNettyRemotingClient().invokeSync(registryAddress, transporter, 3000);
        if (response != null) {
            AckCustomBody ackCustomBody = SerializerHolder.serializerImpl().readObject(response.getBytes(), AckCustomBody.class);
            if (ackCustomBody.isSuccess()) {
                messagesNonAcks.remove(ackCustomBody.getRequestId());
            } else {
                logger.warn("registry center handler timeout");
            }
        }
    }

    public void checkPublishFailMessage() throws InterruptedException, RemotingException{
        if(messagesNonAcks.keySet() != null && messagesNonAcks.keySet().size() > 0){
            logger.warn("have [{}] message send failed,send again",messagesNonAcks.keySet().size());
            for(MessageNonAck ack : messagesNonAcks.values()){
                pushPublishServiceToRegistry(ack.getMsg(), ack.getAddress());
            }
        }
    }

    static class MessageNonAck {
        private final long id;
        private final RemotingTransporter msg;
        private final String address;
        private final long timestamp = SystemClock.millisClock().now();

        public MessageNonAck(RemotingTransporter msg, String address) {
            this.msg = msg;
            this.address = address;
            id = msg.getOpaque();
        }

        public long getId() {
            return id;
        }

        public RemotingTransporter getMsg() {
            return msg;
        }

        public String getAddress() {
            return address;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

}
