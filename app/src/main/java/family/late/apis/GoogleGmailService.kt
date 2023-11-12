package family.late.apis

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage


object GoogleGmailService {

    private const val APPLICATION_NAME = "Late"

    private fun getGmailService(accessToken: String): Gmail {
        val transport = NetHttpTransport()
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()

        val credential = com.google.api.client.googleapis.auth.oauth2.GoogleCredential()
            .setAccessToken(accessToken)

        return Gmail.Builder(transport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    suspend fun sendEmail(accessToken: String, sender: String, subject: String, body: String, recipients: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            val service = getGmailService(accessToken)

            val mimeMessage = createEmail(sender, subject, body, recipients)
            val message = createMessageWithEmail(mimeMessage)

            val response = service.users().messages().send(sender, message).execute()
            response != null
        }
    }


    private fun createEmail(
        sender: String,
        subject: String,
        body: String,
        recipients: List<String>
    ): MimeMessage {
        val props = Properties()
        val session: Session = Session.getDefaultInstance(props, null)
        val email = MimeMessage(session)
        email.setFrom(InternetAddress(sender))
        recipients.forEach {
            email.addRecipient(
                jakarta.mail.Message.RecipientType.TO,
                InternetAddress(it)
        )}

        email.subject = subject
        email.setText(body)
        return email
    }


    private fun createMessageWithEmail(mimeMessage: MimeMessage): Message {
        val byteArrayOutputStream = ByteArrayOutputStream()
        mimeMessage.writeTo(byteArrayOutputStream)
        val encodedEmail = Base64.encodeBase64URLSafeString(byteArrayOutputStream.toByteArray())
        val message = Message()
        message.raw = encodedEmail
        return message
    }
}