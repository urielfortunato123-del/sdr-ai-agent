package com.uriel.viacom.beta016;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String SERVICE_ID = "com.uriel.viacom.nearby.v1";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final int REQ_NEARBY = 700;
    private static final int REQ_AUDIO = 701;
    private static final int REQ_LOCATION = 702;
    private static final int REQ_CAMERA_PERMISSION = 703;
    private static final int REQ_CAMERA_CAPTURE = 704;

    private final int BG = Color.rgb(4, 17, 27);
    private final int CARD = Color.rgb(13, 37, 53);
    private final int CARD_DARK = Color.rgb(8, 29, 43);
    private final int TEAL = Color.rgb(82, 215, 208);
    private final int TEXT = Color.rgb(240, 246, 250);
    private final int MUTED = Color.rgb(156, 178, 194);
    private final int RED = Color.rgb(255, 84, 100);
    private final int GREEN = Color.rgb(72, 220, 160);

    private ConnectionsClient connectionsClient;
    private SharedPreferences prefs;
    private String displayName = "";
    private String activeEndpointId;
    private boolean searching = false;
    private boolean pendingSearchAfterPermission = false;

    private final Map<String, String> discovered = new LinkedHashMap<>();
    private final Map<String, String> connected = new LinkedHashMap<>();
    private final Map<String, String> pendingNames = new HashMap<>();
    private final Map<Long, Payload> incomingFiles = new HashMap<>();
    private final Map<Long, String> incomingTypes = new HashMap<>();
    private final List<String> events = new ArrayList<>();

    private TextView connectionTitle;
    private TextView connectionDetail;
    private TextView statusDot;
    private TextView searchHint;
    private TextView eventsView;
    private TextView versionView;
    private LinearLayout deviceList;
    private Button searchButton;
    private Button talkButton;
    private Button photoButton;
    private Button locationButton;

    private MediaRecorder recorder;
    private File recordingFile;
    private boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences("viacom", MODE_PRIVATE);
        displayName = prefs.getString("display_name", "").trim();
        connectionsClient = Nearby.getConnectionsClient(this);

        buildUi();
        addEvent("ViaCom 0.2.0 iniciado");

        if (displayName.isEmpty()) {
            showNameDialog(false);
        } else if (hasNearbyPermissions()) {
            startAdvertisingOnly();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(18));
        TextView logo = text("VC", 22, true, TEAL);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(roundRect(Color.rgb(8, 32, 45), TEAL, 18, 2));
        header.addView(logo, new LinearLayout.LayoutParams(dp(66), dp(66)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(14), 0, 0, 0);
        TextView title = text("ViaCom", 31, true, TEXT);
        TextView subtitle = text("COMUNICAÇÃO LOCAL • SEM INTERNET", 13, false, MUTED);
        subtitle.setLetterSpacing(.08f);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button settings = smallButton("⚙");
        settings.setOnClickListener(v -> showNameDialog(false));
        header.addView(settings, new LinearLayout.LayoutParams(dp(58), dp(58)));
        root.addView(header);

        LinearLayout connectionCard = card();
        connectionCard.addView(label("CONEXÃO PRÓXIMA"));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.TOP);
        LinearLayout statusTextBox = new LinearLayout(this);
        statusTextBox.setOrientation(LinearLayout.VERTICAL);
        connectionTitle = text("Nenhum aparelho conectado", 24, true, TEXT);
        connectionDetail = text("Toque em Buscar aparelhos para encontrar outros ViaCom próximos.", 15, false, MUTED);
        connectionDetail.setPadding(0, dp(5), dp(12), 0);
        statusTextBox.addView(connectionTitle);
        statusTextBox.addView(connectionDetail);
        statusRow.addView(statusTextBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusDot = text("●", 22, true, RED);
        statusRow.addView(statusDot);
        connectionCard.addView(statusRow);

        searchButton = primaryButton("BUSCAR APARELHOS");
        searchButton.setOnClickListener(v -> ensurePermissionsAndSearch());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        searchLp.topMargin = dp(16);
        connectionCard.addView(searchButton, searchLp);

        searchHint = text("O ViaCom procura somente outros celulares com o ViaCom aberto. Na primeira vez, permita 'Aparelhos próximos'.", 13, false, MUTED);
        searchHint.setPadding(dp(2), dp(10), dp(2), 0);
        connectionCard.addView(searchHint);

        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        deviceList.setPadding(0, dp(12), 0, 0);
        connectionCard.addView(deviceList);
        root.addView(connectionCard, lpTop(14));

        LinearLayout targetCard = card();
        targetCard.addView(label("FALAR COM"));
        TextView target = text("Aparelho conectado", 18, true, TEXT);
        target.setId(View.generateViewId());
        targetCard.addView(target);
        TextView secure = text("Conexão direta protegida pelo Nearby Connections", 13, false, GREEN);
        secure.setPadding(0, dp(4), 0, 0);
        targetCard.addView(secure);
        root.addView(targetCard, lpTop(14));

        TextView touchLabel = label("TOQUE PARA FALAR");
        touchLabel.setGravity(Gravity.CENTER);
        touchLabel.setPadding(0, dp(22), 0, dp(10));
        root.addView(touchLabel);

        talkButton = new Button(this);
        talkButton.setText("FALAR\nCONECTE UM CELULAR");
        talkButton.setTextColor(TEXT);
        talkButton.setTextSize(22);
        talkButton.setTypeface(Typeface.DEFAULT_BOLD);
        talkButton.setAllCaps(false);
        talkButton.setGravity(Gravity.CENTER);
        talkButton.setBackground(roundRect(Color.rgb(12, 54, 70), TEAL, 110, 2));
        talkButton.setEnabled(false);
        talkButton.setOnClickListener(v -> toggleRecording());
        LinearLayout.LayoutParams talkLp = new LinearLayout.LayoutParams(dp(230), dp(230));
        talkLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(talkButton, talkLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(22), 0, 0);
        photoButton = actionButton("▣\nFoto");
        locationButton = actionButton("⌖\nLocalização");
        photoButton.setEnabled(false);
        locationButton.setEnabled(false);
        photoButton.setOnClickListener(v -> capturePhoto());
        locationButton.setOnClickListener(v -> sendLocation());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(128), 1);
        half.rightMargin = dp(8);
        actionRow.addView(photoButton, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(128), 1);
        half2.leftMargin = dp(8);
        actionRow.addView(locationButton, half2);
        root.addView(actionRow);

        LinearLayout eventsCard = card();
        eventsCard.addView(label("ATIVIDADE"));
        eventsCard.addView(text("Últimos eventos", 23, true, TEXT));
        eventsView = text("Nenhum evento ainda.", 14, false, MUTED);
        eventsView.setPadding(0, dp(12), 0, 0);
        eventsCard.addView(eventsView);
        root.addView(eventsCard, lpTop(16));

        LinearLayout infoCard = card();
        versionView = text("BETA 0.2.0 • NEARBY NATIVO", 13, true, TEAL);
        infoCard.addView(versionView);
        TextView info = text("• Busca real de aparelhos próximos, sem QR.\n• Bluetooth, BLE e Wi‑Fi são escolhidos automaticamente pelo Nearby Connections.\n• Voz, foto e GPS usam o mesmo enlace direto.\n• Próxima etapa: retransmissão mesh entre vários ViaCom.", 14, false, MUTED);
        info.setPadding(0, dp(10), 0, 0);
        infoCard.addView(info);
        root.addView(infoCard, lpTop(16));

        TextView footer = text("ViaCom • comunicação offline entre aparelhos próximos", 12, false, Color.rgb(91, 117, 135));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(22), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void ensurePermissionsAndSearch() {
        if (displayName.isEmpty()) {
            showNameDialog(true);
            return;
        }
        if (!hasNearbyPermissions()) {
            pendingSearchAfterPermission = true;
            requestPermissions(requiredNearbyPermissions(), REQ_NEARBY);
            return;
        }
        startNearbySearch();
    }

    private String[] requiredNearbyPermissions() {
        List<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 32) {
            p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (Build.VERSION.SDK_INT <= 31) {
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return p.toArray(new String[0]);
    }

    private boolean hasNearbyPermissions() {
        for (String permission : requiredNearbyPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void startNearbySearch() {
        if (!hasNearbyPermissions()) return;
        discovered.clear();
        refreshDeviceList();
        searching = true;
        searchButton.setText("BUSCANDO...");
        searchHint.setText("Procurando ViaCom próximos. Deixe o outro celular com o ViaCom aberto.");
        addEvent("Busca de aparelhos próximos iniciada");

        connectionsClient.stopDiscovery();
        connectionsClient.stopAdvertising();
        startAdvertisingInternal();

        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> runOnUiThread(() -> {
                    connectionDetail.setText("Buscando aparelhos ViaCom próximos...");
                    addEvent("Descoberta Nearby ativa");
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyError("Busca", e)));
    }

    private void startAdvertisingOnly() {
        if (!hasNearbyPermissions() || displayName.isEmpty()) return;
        startAdvertisingInternal();
    }

    private void startAdvertisingInternal() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(displayName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> runOnUiThread(() -> addEvent("Este aparelho está visível no ViaCom")))
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyError("Disponibilidade", e)));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            discovered.put(endpointId, info.getEndpointName());
            runOnUiThread(() -> {
                refreshDeviceList();
                connectionDetail.setText(discovered.size() + " aparelho(s) encontrado(s). Toque para conectar.");
                addEvent("Encontrado: " + info.getEndpointName());
            });
        }

        @Override
        public void onEndpointLost(String endpointId) {
            discovered.remove(endpointId);
            runOnUiThread(() -> {
                refreshDeviceList();
                addEvent("Aparelho saiu do alcance");
            });
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
            pendingNames.put(endpointId, info.getEndpointName());
            runOnUiThread(() -> showConnectionApproval(endpointId, info));
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution resolution) {
            runOnUiThread(() -> {
                if (resolution.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                    String name = pendingNames.getOrDefault(endpointId, discovered.getOrDefault(endpointId, "Aparelho ViaCom"));
                    connected.put(endpointId, name);
                    activeEndpointId = endpointId;
                    setConnectedUi(name);
                    addEvent("Conectado a " + name);
                } else {
                    addEvent("Conexão recusada/falhou: " + resolution.getStatus().getStatusCode());
                    Toast.makeText(MainActivity.this, "Não foi possível conectar.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onDisconnected(String endpointId) {
            String name = connected.remove(endpointId);
            if (endpointId.equals(activeEndpointId)) activeEndpointId = null;
            runOnUiThread(() -> {
                setDisconnectedUi();
                addEvent("Desconectado" + (name == null ? "" : " de " + name));
            });
        }
    };

    private void showConnectionApproval(String endpointId, ConnectionInfo info) {
        String code = info.getAuthenticationDigits();
        new AlertDialog.Builder(this)
                .setTitle("Solicitação de conexão")
                .setMessage(info.getEndpointName() + " quer se conectar.\n\nCódigo de segurança: " + code + "\n\nConfirme se o mesmo código aparece no outro aparelho.")
                .setPositiveButton("Aceitar", (d, w) -> {
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                    connectionDetail.setText("Confirmando conexão com " + info.getEndpointName() + "...");
                    addEvent("Conexão aceita • código " + code);
                })
                .setNegativeButton("Recusar", (d, w) -> {
                    connectionsClient.rejectConnection(endpointId);
                    addEvent("Conexão recusada");
                })
                .setCancelable(false)
                .show();
    }

    private void requestConnection(String endpointId, String name) {
        if (activeEndpointId != null && activeEndpointId.equals(endpointId)) return;
        connectionDetail.setText("Solicitando conexão com " + name + "...");
        addEvent("Solicitando conexão com " + name);
        connectionsClient.requestConnection(displayName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyError("Conexão", e)));
    }

    private void refreshDeviceList() {
        deviceList.removeAllViews();
        if (discovered.isEmpty()) {
            if (searching) {
                TextView empty = text("Nenhum ViaCom encontrado ainda...", 14, false, MUTED);
                empty.setPadding(dp(4), dp(6), dp(4), dp(6));
                deviceList.addView(empty);
            }
            return;
        }
        for (Map.Entry<String, String> entry : discovered.entrySet()) {
            String endpointId = entry.getKey();
            String name = entry.getValue();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            row.setBackground(roundRect(CARD_DARK, Color.rgb(29, 68, 88), 14, 1));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(text(name, 17, true, TEXT));
            texts.addView(text(connected.containsKey(endpointId) ? "Conectado" : "ViaCom próximo", 12, false, connected.containsKey(endpointId) ? GREEN : MUTED));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button b = primaryButton(connected.containsKey(endpointId) ? "CONECTADO" : "CONECTAR");
            b.setEnabled(!connected.containsKey(endpointId));
            b.setOnClickListener(v -> requestConnection(endpointId, name));
            row.addView(b, new LinearLayout.LayoutParams(dp(118), dp(48)));

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(8);
            deviceList.addView(row, rp);
        }
    }

    private void setConnectedUi(String name) {
        connectionTitle.setText("Conectado a " + name);
        connectionDetail.setText("Enlace Nearby ativo. Voz, foto e localização disponíveis.");
        statusDot.setTextColor(GREEN);
        searchButton.setText("BUSCAR MAIS APARELHOS");
        talkButton.setEnabled(true);
        talkButton.setText("FALAR\nTOQUE PARA INICIAR");
        photoButton.setEnabled(true);
        locationButton.setEnabled(true);
        refreshDeviceList();
    }

    private void setDisconnectedUi() {
        connectionTitle.setText("Nenhum aparelho conectado");
        connectionDetail.setText("Toque em Buscar aparelhos para encontrar outros ViaCom próximos.");
        statusDot.setTextColor(RED);
        talkButton.setEnabled(false);
        talkButton.setText("FALAR\nCONECTE UM CELULAR");
        photoButton.setEnabled(false);
        locationButton.setEnabled(false);
    }

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                if (bytes != null) handleBytes(endpointId, new String(bytes, StandardCharsets.UTF_8));
            } else if (payload.getType() == Payload.Type.FILE) {
                incomingFiles.put(payload.getId(), payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Payload p = incomingFiles.get(update.getPayloadId());
                if (p != null) runOnUiThread(() -> processIncomingFile(update.getPayloadId(), p));
            } else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) {
                runOnUiThread(() -> addEvent("Falha ao receber arquivo"));
            }
        }
    };

    private void handleBytes(String endpointId, String message) {
        runOnUiThread(() -> {
            if (message.startsWith("META|")) {
                String[] parts = message.split("\\|", 4);
                if (parts.length >= 3) {
                    try {
                        long id = Long.parseLong(parts[1]);
                        incomingTypes.put(id, parts[2]);
                        Payload p = incomingFiles.get(id);
                        if (p != null) processIncomingFile(id, p);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (message.startsWith("GPS|")) {
                String[] parts = message.split("\\|", 6);
                if (parts.length >= 5) {
                    String lat = parts[1];
                    String lon = parts[2];
                    String acc = parts[3];
                    String sender = parts[4];
                    addEvent("Localização recebida de " + sender);
                    new AlertDialog.Builder(this)
                            .setTitle("Localização recebida")
                            .setMessage(sender + "\nLatitude: " + lat + "\nLongitude: " + lon + "\nPrecisão: ±" + acc + " m")
                            .setPositiveButton("Abrir mapa", (d, w) -> {
                                try {
                                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon));
                                    startActivity(i);
                                } catch (Exception e) {
                                    Toast.makeText(this, "Nenhum app de mapa disponível", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Fechar", null)
                            .show();
                }
            }
        });
    }

    private void processIncomingFile(long id, Payload payload) {
        String type = incomingTypes.get(id);
        if (type == null) return;
        Uri uri = payload.asFile() == null ? null : payload.asFile().asUri();
        if (uri == null) return;

        incomingFiles.remove(id);
        incomingTypes.remove(id);

        if ("AUDIO".equals(type)) {
            addEvent("Áudio recebido");
            playAudio(uri);
        } else if ("PHOTO".equals(type)) {
            addEvent("Foto recebida");
            new AlertDialog.Builder(this)
                    .setTitle("Foto recebida")
                    .setMessage("Uma foto chegou pelo ViaCom.")
                    .setPositiveButton("Abrir", (d, w) -> {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(uri, "image/jpeg");
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(this, "Foto recebida, mas não foi possível abrir automaticamente", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Fechar", null)
                    .show();
        }
    }

    private void playAudio(Uri uri) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(this, uri);
            player.setOnCompletionListener(MediaPlayer::release);
            player.prepare();
            player.start();
            Toast.makeText(this, "Reproduzindo áudio recebido", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            addEvent("Erro ao reproduzir áudio: " + e.getClass().getSimpleName());
        }
    }

    private void toggleRecording() {
        if (activeEndpointId == null) {
            ensurePermissionsAndSearch();
            return;
        }
        if (recording) {
            stopRecordingAndSend();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = new File(getCacheDir(), "viacom_voice_" + System.currentTimeMillis() + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            talkButton.setText("PARAR E ENVIAR\nGRAVANDO...");
            addEvent("Gravação iniciada");
        } catch (Exception e) {
            recording = false;
            addEvent("Erro no microfone: " + e.getClass().getSimpleName());
            Toast.makeText(this, "Não foi possível iniciar o microfone", Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecordingAndSend() {
        if (!recording || recorder == null) return;
        try {
            recorder.stop();
            recorder.reset();
            recorder.release();
            recorder = null;
            recording = false;
            talkButton.setText("FALAR\nTOQUE PARA INICIAR");
            sendFile(recordingFile, "AUDIO", "m4a");
            addEvent("Áudio enviado");
        } catch (RuntimeException e) {
            recording = false;
            talkButton.setText("FALAR\nTOQUE PARA INICIAR");
            addEvent("Gravação curta demais");
        }
    }

    private void capturePhoto() {
        if (activeEndpointId == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        try {
            startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Câmera indisponível", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA_CAPTURE && resultCode == RESULT_OK && data != null) {
            Object obj = data.getExtras() == null ? null : data.getExtras().get("data");
            if (obj instanceof Bitmap) {
                try {
                    File f = new File(getCacheDir(), "viacom_photo_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        ((Bitmap) obj).compress(Bitmap.CompressFormat.JPEG, 88, out);
                    }
                    sendFile(f, "PHOTO", "jpg");
                    addEvent("Foto enviada");
                } catch (IOException e) {
                    addEvent("Erro ao preparar foto");
                }
            }
        }
    }

    private void sendLocation() {
        if (activeEndpointId == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        try {
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            best = gps != null ? gps : net;
            if (gps != null && net != null && net.getTime() > gps.getTime()) best = net;
        } catch (SecurityException ignored) {}

        if (best != null) {
            deliverLocation(best);
            return;
        }

        connectionDetail.setText("Obtendo localização GPS...");
        try {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override public void onLocationChanged(Location location) { deliverLocation(location); }
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            }, Looper.getMainLooper());
        } catch (Exception e) {
            Toast.makeText(this, "Ative a localização do celular", Toast.LENGTH_LONG).show();
        }
    }

    private void deliverLocation(Location location) {
        String msg = "GPS|" + location.getLatitude() + "|" + location.getLongitude() + "|" + Math.round(location.getAccuracy()) + "|" + displayName;
        sendBytes(msg);
        addEvent("Localização enviada • ±" + Math.round(location.getAccuracy()) + " m");
        String name = connected.getOrDefault(activeEndpointId, "aparelho");
        connectionDetail.setText("Conectado a " + name + " • localização enviada");
    }

    private void sendFile(File file, String type, String extension) {
        if (activeEndpointId == null || file == null || !file.exists()) return;
        try {
            Payload payload = Payload.fromFile(file);
            payload.setFileName("ViaCom_" + type.toLowerCase(Locale.ROOT) + "_" + System.currentTimeMillis() + "." + extension);
            long id = payload.getId();
            sendBytes("META|" + id + "|" + type + "|" + displayName);
            connectionsClient.sendPayload(activeEndpointId, payload)
                    .addOnFailureListener(e -> runOnUiThread(() -> addEvent("Falha ao enviar " + type.toLowerCase(Locale.ROOT))));
        } catch (Exception e) {
            addEvent("Erro ao enviar arquivo: " + e.getClass().getSimpleName());
        }
    }

    private void sendBytes(String message) {
        if (activeEndpointId == null) return;
        connectionsClient.sendPayload(activeEndpointId, Payload.fromBytes(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = true;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;

        if (requestCode == REQ_NEARBY) {
            if (granted && pendingSearchAfterPermission) {
                pendingSearchAfterPermission = false;
                startNearbySearch();
            } else if (!granted) {
                addEvent("Permissão de aparelhos próximos negada");
                Toast.makeText(this, "O ViaCom precisa da permissão 'Aparelhos próximos' para buscar celulares.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_AUDIO && granted) {
            startRecording();
        } else if (requestCode == REQ_LOCATION && granted) {
            sendLocation();
        } else if (requestCode == REQ_CAMERA_PERMISSION && granted) {
            capturePhoto();
        }
    }

    private void showNameDialog(boolean searchAfter) {
        final EditText input = new EditText(this);
        input.setText(displayName);
        input.setHint("Nome visível");
        input.setSingleLine(true);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nome deste aparelho")
                .setMessage("Digite o nome que ficará visível para aparelhos próximos.")
                .setView(box)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = input.getText().toString().trim();
            if (n.isEmpty()) {
                input.setError("Digite um nome");
                return;
            }
            displayName = n;
            prefs.edit().putString("display_name", n).apply();
            addEvent("Nome do aparelho salvo: " + n);
            dialog.dismiss();
            if (searchAfter) ensurePermissionsAndSearch();
            else if (hasNearbyPermissions()) startAdvertisingOnly();
        }));
        dialog.show();
    }

    private void nearbyError(String stage, Exception e) {
        searching = false;
        searchButton.setText("BUSCAR APARELHOS");
        connectionDetail.setText(stage + " não iniciou. Verifique Bluetooth/Wi‑Fi e as permissões.");
        addEvent(stage + " falhou: " + e.getClass().getSimpleName());
        Toast.makeText(this, stage + " falhou. Verifique Bluetooth, Wi‑Fi e 'Aparelhos próximos'.", Toast.LENGTH_LONG).show();
    }

    private void addEvent(String message) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        events.add(0, time + "  " + message);
        while (events.size() > 8) events.remove(events.size() - 1);
        if (eventsView != null) eventsView.setText(String.join("\n\n", events));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { connectionsClient.stopAdvertising(); } catch (Exception ignored) {}
        try { connectionsClient.stopDiscovery(); } catch (Exception ignored) {}
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) {}
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(18), dp(18), dp(18));
        l.setBackground(roundRect(CARD, Color.rgb(35, 70, 91), 20, 1));
        return l;
    }

    private TextView label(String s) {
        TextView t = text(s, 12, true, Color.rgb(132, 159, 180));
        t.setLetterSpacing(.12f);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.rgb(218, 255, 252));
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(Color.rgb(18, 76, 82), TEAL, 14, 1));
        return b;
    }

    private Button smallButton(String s) {
        Button b = primaryButton(s);
        b.setTextSize(23);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundRect(CARD_DARK, Color.rgb(45, 72, 91), 16, 1));
        return b;
    }

    private Button actionButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(20), dp(10), dp(10), dp(10));
        b.setBackground(roundRect(CARD, Color.rgb(35, 70, 91), 20, 1));
        return b;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lpTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
