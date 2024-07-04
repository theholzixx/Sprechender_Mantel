package com.sprechender_mantel;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import javax.security.auth.login.LoginException;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DiscordBot extends ListenerAdapter {

    private static final String API_URL = "http://localhost:11434/api/generate";
    private static final HttpClient client = HttpClient.newHttpClient();
    
    String CHANNEL_NAME = "DNDantel";
    static BufferedWriter writer;
    static String History = "";
    static TextChannel textChannel;
    static boolean start = false;
    
    public static void main(String[] args) throws LoginException {

        String data = "Error";
        try {
            File myObj = new File("token.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                data = myReader.nextLine();
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        
        File historyFile = new File("History.txt");
        File Temp = new File("Tempfile.txt");

        try {
            BufferedReader reader = new BufferedReader(new FileReader(historyFile));
        
            String currentline = "";
            boolean first = true;
            while ((currentline = reader.readLine()) != null) {
                if (first) {
                    History = currentline;
                    first = false;
                } else { 
                    History = History + "\n" + currentline;
                }
            }
            reader.close();
            writer = new BufferedWriter(new FileWriter(Temp));
            writer.write(History);
            writer.flush();
            System.out.println(History);
        } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new Error();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new Error();
        }

        JDABuilder.createLight(data, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                  .addEventListeners(new DiscordBot())
                  .build();

        

        // Start a timer to send a "?" every 10 minutes
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (start) {
                    try {
                        String recieved = "?";
                        System.out.println("\nNutzer: " + recieved);
                        writer.write("\nNutzer: " + recieved);
                        History = History + "\nNutzer: " + recieved;
                        String response = getOpenLLaMAResponse(History.replaceAll("\n", "  "));
                        textChannel.sendMessage(response).queue();
                        System.out.println("\nKI: " + response);
                        writer.write("\nKI: " + response);
                        History = History + "\nKI: " + response;
                        writer.flush();
                        System.out.println("Timer!");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
            }
        }, 0, ThreadLocalRandom.current().nextInt(180000, 600000)); // 600000 ms = 10 minutes

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.out.println("In shutdown hook");
                historyFile.delete();
                try {
                    FileUtils.copyFile(Temp, historyFile);
                    writer.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } 
            }
        }, "Shutdown-thread"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getMessage().getContentRaw().startsWith("§")) return;
        if (event.getMessage().getContentRaw().startsWith("§Start")){
            start = true;
            System.out.println("ES GEHT LOS!");
            return;
        }
        if (!start) start = true;
        
        String message = event.getMessage().getContentRaw();
        textChannel = event.getGuild().getTextChannelsByName(CHANNEL_NAME,true).get(0);

        try {
            String recieved = message.replaceFirst("§", "");
            System.out.println("\nNutzer: " + recieved);
            writer.write("\nNutzer: " + recieved);
            History = History + "\nNutzer: " + recieved;
            String response = getOpenLLaMAResponse(History.replaceAll("\n", "  "));
            textChannel.sendMessage(response).queue();
            System.out.println("\nKI: " + response);
            writer.write("\nKI: " + response);
            History = History + "\nKI: " + response;
            writer.flush();
            System.out.println("Anfrage!");
        } catch (Exception e) {
            e.printStackTrace();
            textChannel.sendMessage("Es gab einen Fehler bei der Verarbeitung deiner Anfrage.").queue();
        }
    }

    private static String getOpenLLaMAResponse(String prompt) throws Exception {
        String requestBody = String.format("{\"model\": \"Llama3\", \"stream\": false, \"prompt\": \"%s\"}", prompt.replaceAll("\n", "  ").replaceAll("\"", "_")); //String requestBody = String.format("{\"model\": \"MagischerMantel\", \"stream\": false, \"prompt\": \"%s\"}", prompt);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        // Parse the response to extract the text
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        
        return jsonObject.get("response").getAsString().replace("\\n", "").replace("\\\"", "\"").trim();
    }
}
