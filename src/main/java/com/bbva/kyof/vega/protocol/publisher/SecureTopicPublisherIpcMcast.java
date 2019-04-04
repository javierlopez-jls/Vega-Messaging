package com.bbva.kyof.vega.protocol.publisher;

import com.bbva.kyof.vega.config.general.TopicSecurityTemplateConfig;
import com.bbva.kyof.vega.config.general.TopicTemplateConfig;
import com.bbva.kyof.vega.exception.VegaException;
import com.bbva.kyof.vega.msg.MsgType;
import com.bbva.kyof.vega.msg.PublishResult;
import com.bbva.kyof.vega.protocol.common.VegaContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Topic secure publisher implementation for multicast and ipc.
 *
 * In this case there is a single related aeron publisher because the sender is the end-point.
 *
 * The class is thread-safe
 */
@Slf4j
class SecureTopicPublisherIpcMcast extends TopicPublisherIpcMcast
{
    /** Topic security configuration, null if security is not configured */
    @Getter private final TopicSecurityTemplateConfig topicSecurityConfig;

    /** Encoder to encrypt the user messages */
    private final AesTopicMsgEncoder topicMsgEncoder;

    /** Reusable buffer that will be used to wrap the encoded messages before sending */
    private UnsafeBuffer encryptedUnsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(0));

    /**
     * Constructor of the class
     *
     * @param topicName Topic name that is going to sendMsg
     * @param topicConfig topic configuration
     * @param topicSecurityConfig security template configuration, null if not secured
     * @param vegaContext library instance configuration
     */
    SecureTopicPublisherIpcMcast(
            final String topicName,
            final TopicTemplateConfig topicConfig,
            final VegaContext vegaContext,
            final TopicSecurityTemplateConfig topicSecurityConfig) throws VegaException
    {
        super(topicName, topicConfig, vegaContext);
        this.topicSecurityConfig = topicSecurityConfig;
        this.topicMsgEncoder = new AesTopicMsgEncoder();
    }

    /**
     * Returns the encryption session key
     */
    byte[] getSessionKey()
    {
        return this.topicMsgEncoder.getAESKey();
    }

    @Override
    protected PublishResult sendToAeron(final DirectBuffer message, final int offset, final int length)
    {
        // Encrypt the message
        final ByteBuffer encrypedMsg;
        try
        {
            encrypedMsg = this.topicMsgEncoder.encryptMessage(message, offset, length);
        }
        catch (VegaException e)
        {
            log.error("Unexpected error trying to encrypt a message before sending it in a secure topic publisher", e);
            return PublishResult.UNEXPECTED_ERROR;
        }

        // Wrap the encoded message prior to send
        this.encryptedUnsafeBuffer.wrap(encrypedMsg);

        // Send the message
        return this.aeronPublisher.sendMessage(MsgType.ENCRYPTED_DATA, this.getUniqueId(), this.encryptedUnsafeBuffer, 0, encrypedMsg.limit());
    }

    @Override
    boolean hasSecurity()
    {
        return true;
    }
}