package br.com.stringtracker.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "expo-push")
@Path("/--/api/v2/push")
public interface ExpoPushClient {

    @POST
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> send(List<ExpoPushMessage> messages);

    record ExpoPushMessage(
            String to,
            String title,
            String body,
            Map<String, Object> data,
            String sound
    ) {
        public static ExpoPushMessage of(String to, String title, String body, Map<String, Object> data) {
            return new ExpoPushMessage(to, title, body, data, "default");
        }
    }
}
