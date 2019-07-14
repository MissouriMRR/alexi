/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

public class PocketSphinxActivity extends Activity implements
        RecognitionListener {

    private final static String QUEUE_NAME = "image-queue";
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String NAMES_SEARCH = "name";
    private static final String COMMAND_SEARCH = "command";
    private static final String DUR_SEARCH = "duration";
    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "activate";
    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    public int ctr = 0;
    public byte[] bt;
    public Bitmap bit;
    public String RabbitHOST = "10.0.2.2";
    public String IP;
    public int PORT;
    public String NAME;
    public String COMMAND;
    public String DUR;
    TCPClient mTCPClient;
    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    public void decodeMsg(byte[] body, ImageView image) {
        if (ctr == 1) {
            bt = Base64.decode(body, Base64.DEFAULT);
            bit = BitmapFactory.decodeByteArray(bt, 0, bt.length);
            runOnUiThread(() -> image.setImageBitmap(bit));
            ctr = 0;
        } else {
            ctr++;
        }
        return;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            ImageView image = findViewById(R.id.image);
            decodeMsg(delivery.getBody(), image);
        };

        // Prepare the data for UI
        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(NAMES_SEARCH, R.string.names_caption);
        captions.put(COMMAND_SEARCH, R.string.command_caption);
        captions.put(DUR_SEARCH, R.string.dur_caption);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");


        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }


        new SetupTask(this).execute();


        //postRequest(postUrl, postBody);
        //if (mTCPClient != null) {
        //    mTCPClient.sendMessage(postBody);
        //}

        Button button = findViewById(R.id.button_connect);
        button.setOnClickListener(v -> {
            Button button_connect = findViewById(R.id.button_connect);
            EditText serverBox = findViewById(R.id.serverBox);
            EditText portBox = findViewById(R.id.portBox);
            EditText rabbitHOST = findViewById(R.id.RabbitHostBox);

            IP = serverBox.getText().toString();
            PORT = Integer.parseInt(portBox.getText().toString());
            //new ConnectTask().execute("");

            RabbitHOST = rabbitHOST.getText().toString();
            //new SetupVid(this).execute(deliverCallback);

            RunConnections();

            button_connect.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.Green), PorterDuff.Mode.MULTIPLY);
        });


        //new SetupVid(this).execute(deliverCallback);
    }

    public void RunConnections() {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            ImageView image = findViewById(R.id.image);
            decodeMsg(delivery.getBody(), image);
        };

        new SetupVid(this).execute(deliverCallback);
        new ConnectTask().execute("");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }

    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        ((TextView) findViewById(R.id.result_text)).setText(text);

        Log.d("STUFF", "onPartialResult: " + text);
        if (text.equals(KEYPHRASE))
            switchSearch(NAMES_SEARCH);
        else if (text.equals("mike ")
                || text.equals("rick")
                || text.equals("bingo")) {
            NAME = text;
            switchSearch(COMMAND_SEARCH);
        } else if (text.equals("forward ")
                || text.equals("backward ")
                || text.equals("left ")
                || text.equals("right ")
                || text.equals("land ")
                || text.equals("stop ")
                || text.equals("takeoff ")
                || text.equals("heal ")) {
            COMMAND = text;
            switchSearch(DUR_SEARCH);
        } else if (text.equals("one ")
                || text.equals("two ")
                || text.equals("three ")) {
            DUR = text;
            switchSearch(KWS_SEARCH);
        } else {
            NAME = null;
            COMMAND = null;
            DUR = null;
            switchSearch(KWS_SEARCH);
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (mTCPClient != null) {
            ((TextView) findViewById(R.id.result_text)).setText("");

            String msg;

            if (NAME != null && COMMAND != null && DUR != null)
                msg = NAME.concat(COMMAND).concat(DUR);
            else
                msg = null;

            if (msg != null) {
                makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                Log.d("VOICE", "\"" + msg + "\" \"" + KEYPHRASE + "\"");
                mTCPClient.sendMessage(msg);
                NAME = null;
                COMMAND = null;
                DUR = null;
            }
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {

    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (6000 ms or 6 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 6000);

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        File NAMES_GRAM = new File(assetsDir, "names.gram");
        recognizer.addKeywordSearch(NAMES_SEARCH, NAMES_GRAM);

        // Drone command
        File COMMANDS_GRAM = new File(assetsDir, "commands.gram");
        recognizer.addKeywordSearch(COMMAND_SEARCH, COMMANDS_GRAM);

        File DUR_GRAM = new File(assetsDir, "dur.gram");
        recognizer.addKeywordSearch(DUR_SEARCH, DUR_GRAM);
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());

    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    //Setup task for speech recognizer
    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<PocketSphinxActivity> activityReference;

        SetupTask(PocketSphinxActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                ((TextView) activityReference.get().findViewById(R.id.caption_text))
                        .setText("Failed to init recognizer " + result);
            } else {
                activityReference.get().switchSearch(KWS_SEARCH);
            }
        }
    }

    //Sets up the connection to rabbitMQ for pulling images and begins the basicConsume function
    private class SetupVid extends AsyncTask<DeliverCallback, Void, Channel> {
        WeakReference<PocketSphinxActivity> activityReference;

        SetupVid(PocketSphinxActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Channel doInBackground(DeliverCallback... deliverCallback) {

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RabbitHOST);
            factory.setPort(5672);
            factory.setVirtualHost("/");
            factory.setUsername("jimmy");
            factory.setPassword("johns");

            Channel channel = null;
            try {
                Connection connection = factory.newConnection();
                channel = connection.createChannel();
                channel.basicQos(1);

                channel.basicConsume(QUEUE_NAME, true, deliverCallback[0], consumerTag -> {
                });

            } catch (TimeoutException | IOException e) {
                e.printStackTrace();
                Log.d("CONN", "exception thrown");
            }
            return channel;
        }

        @Override
        protected void onPostExecute(Channel channel) {
        }
    }

    //Establishes TCP client connection
    public class ConnectTask extends AsyncTask<String, String, TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            //we create a TCPClient object
            //here the messageReceived method is implemented
            mTCPClient = new TCPClient(message1 -> {
                //this method calls the onProgressUpdate
                publishProgress(message1);
            });
            mTCPClient.run(IP, PORT);

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //response received from server
            Log.d("test", "response " + values[0]);
            //process server response here....

        }
    }
}
