package com.sprechender_mantel;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
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
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

import com.github.kilianB.apis.googleTextToSpeech.GLanguage;
import com.github.kilianB.apis.googleTextToSpeech.GoogleTextToSpeech;
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
    static Timer timer = new Timer();
    static GoogleTextToSpeech tts;
    
    public static void main(String[] args) throws LoginException {

        String outputPath = "mpFiles/";

        File outputDirectory = new File(outputPath);
        outputDirectory.mkdirs();
        
        tts = new GoogleTextToSpeech(outputPath);

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

        

        // Start a timer to send a "?" somewhere between 3 and 10 minutes
        
        timer.schedule(MyTimerTask(), 0, ThreadLocalRandom.current().nextInt(180000, 600000)); // 600000 ms = 10 minutes

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
        if (!start){
            start = true;
            VoiceChannel voiceChannel = event.getGuild().getVoiceChannelById("1259233480353517598");
            AudioManager audioManager = voiceChannel.getGuild().getAudioManager();
            audioManager.setSendingHandler(new);
        }
        System.out.println(event.getGuild());
        
        String message = event.getMessage().getContentRaw();
        textChannel = event.getGuild().getTextChannelsByName(CHANNEL_NAME,true).get(0);

        //timer.cancel();
        //timer.schedule(MyTimerTask(), 0, ThreadLocalRandom.current().nextInt(180000, 600000));

        String recieved = message.replaceFirst("§", "");
        GetResponse(recieved);
        System.out.println("Anfrage!");
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

    private static String GetResponse(String recieved){
        File convertedTextMP3;
        try {
            System.out.println("\nNutzer: " + recieved);
            writer.write("\nNutzer: " + recieved);
            History = History + "\nNutzer: " + recieved;
            String response = getOpenLLaMAResponse(History.replaceAll("\n", "  "));
            textChannel.sendMessage(response).queue();
            System.out.println("\nKI: " + response);
            writer.write("\nKI: " + response);
            History = History + "\nKI: " + response;
            writer.flush();
            convertedTextMP3 = tts.convertText(response, GLanguage.German, "FileName");
        } catch(IOException e){
            e.printStackTrace();
            textChannel.sendMessage("Es gab einen Fehler bei der Verarbeitung deiner Anfrage.").queue();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            textChannel.sendMessage("Es gab einen Fehler bei der Verarbeitung deiner Anfrage.").queue();
            return null;
        }
        return convertedTextMP3.getAbsolutePath();
    }

    private static TimerTask MyTimerTask() {
        TimerTask Timer = new TimerTask() {
            @Override
            public void run() {
                if (start) {
                    String recieved = "?";
                    String Path = GetResponse(recieved);
                    System.out.println(Path);
                    System.out.println("Timer!");
                }
            }
        };
        return Timer;
    }

    static class AudioPlayerSendHandler implements AudioSendHandler {
        private final AudioPlayer player;
        private final File audioFile;

        public AudioPlayerSendHandler(File audioFile) {
            this.audioFile = audioFile;
            AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
            playerManager.registerSourceManager(new LocalAudioSourceManager());
            player = playerManager.createPlayer();
            AudioItem item = playerManager.loadItem(audioFile.getAbsolutePath(), null);
            player.playTrack((AudioTrack) item);
        }

        @Override
        public boolean canProvide() {
            return player.provide() != null;
        }

        @Override
        public ByteBuffer provide20MsAudio() {
            return ByteBuffer.wrap(player.provide().getData());
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }

}
