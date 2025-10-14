package com.onefin.posapp.core.services

import com.rabbitmq.client.*
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RabbitMQService @Inject constructor(
    private val storageService: StorageService
) {

    private var isListening = false
    private var channel: Channel? = null
    private var connection: Connection? = null
    private var listener: ((String) -> Unit)? = null
    private var consumerTag: String? = null
    private var listenerJob: Job? = null

    fun setOnMessageListener(listener: (String) -> Unit) {
        this.listener = listener
    }

    fun startListening(posSerial: String) {
        if (isListening) {
            return
        }
        isListening = true

        listenerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Khởi tạo connection nếu chưa có hoặc đã đóng
                if (connection == null || !connection!!.isOpen) {
                    connection = createConnection(posSerial)
                }

                // Khởi tạo channel nếu chưa có hoặc đã đóng
                if (channel == null || !channel!!.isOpen) {
                    channel = connection?.createChannel()
                }

                val queueName = "posapp_$posSerial"
                val routingKey = "Serial_$posSerial"

                // Tự động tạo queue nếu chưa tồn tại
                channel?.queueDeclare(
                    queueName,
                    true,   // durable: Queue không bị mất khi RabbitMQ server restart
                    false,  // exclusive: Các kết nối khác có thể truy cập queue
                    false,  // autoDelete: Queue không bị xóa khi không còn consumer
                    null    // arguments
                )

                // Xóa các message cũ trong queue
                channel?.queuePurge(queueName)

                // Bind queue với exchange
                channel?.queueBind(queueName, com.onefin.posapp.core.config.RabbitConstants.EXCHANGE_NAME, routingKey)

                // Tạo consumer để nhận message
                val consumer = object : DefaultConsumer(channel) {
                    override fun handleDelivery(
                        consumerTag: String?,
                        envelope: Envelope?,
                        properties: AMQP.BasicProperties?,
                        body: ByteArray?
                    ) {
                        body?.let {
                            val message = String(it, Charsets.UTF_8)
                            listener?.invoke(message)
                        }

                        // Acknowledge message
                        envelope?.let {
                            channel?.basicAck(it.deliveryTag, false)
                        }
                    }
                }

                // Bắt đầu consume message
                consumerTag = channel?.basicConsume(queueName, false, consumer)

            } catch (e: Exception) {
                isListening = false
                delay(com.onefin.posapp.core.config.RabbitConstants.RECONNECT_DELAY)
                if (isActive) {
                    startListening(posSerial)
                }
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                consumerTag?.let { channel?.basicCancel(it) }
                channel?.close()
                connection?.close()
            } catch (e: Exception) {
                // Ignore errors during shutdown
            } finally {
                channel = null
                connection = null
                consumerTag = null
                isListening = false
            }
        }
    }

    private fun createConnection(posSerial: String): Connection {
        val account = storageService.getAccount()

        val rabbitUrl = account?.terminal?.systemConfig?.rabbitUrl
            ?.takeIf { it.isNotEmpty() }
            ?: com.onefin.posapp.core.config.RabbitConstants.DEFAULT_RABBIT_URL

        val factory = ConnectionFactory().apply {
            setUri(rabbitUrl)
            networkRecoveryInterval = com.onefin.posapp.core.config.RabbitConstants.RECONNECT_DELAY
            isAutomaticRecoveryEnabled = true
            connectionTimeout = 30000
            requestedHeartbeat = 60
        }

        return factory.newConnection("posapp_$posSerial")
    }

    fun isConnected(): Boolean {
        return connection?.isOpen == true && channel?.isOpen == true && isListening
    }
}