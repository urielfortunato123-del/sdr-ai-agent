package com.uriel.livmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    private static final String SERVICE_ID = "com.uriel.livmessenger.nearby.v1";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final int REQ_NEARBY = 901;

    private final int BG = Color.rgb(7, 16, 24);
    private final int SURFACE = Color.rgb(16, 27, 38);
    private final int SURFACE_2 = Color.rgb(22, 36, 49);
    private final int ACCENT = Color.rgb(66, 211, 196);
    private final int TEXT = Color.rgb(242, 247, 250);
    private final int MUTED = Color.rgb(154, 171, 184);
    private final int GREEN = Color.rgb(83, 222, 159);
    private final int DANGER = Color.rgb(255, 101, 113);

    private ConnectionsClient connectionsClient;
    private android.content.SharedPreferences prefs;

    private String username = "";
    private boolean showNearby = false;
    private boolean inChat = false;
    private boolean pendingDiscoverAfterPermission = false;
    private String activePeerName;
    private String pendingChatEndpoint;

    private LinearLayout contentList;
    private TextView homeSubtitle;
    private Button conversationsTab;
    private Button nearbyTab;
    private Button bottomAction;
    private LinearLayout messagesList;
    private ScrollView messagesScroll;
    private EditText messageInput;
    private TextView chatStatus;

    private final Map<String, String> discovered = new LinkedHashMap<>();
    private final Map<String, String> endpointToName = new HashMap<>();
    private final Map<String, String> nameToEndpoint = new HashMap<>();
    private final Set<String> connectedEndpoints = new HashSet<>();
    private final Map<String, String> pendingNames = new HashMap<>();
    private final Map<String, SecretKey> sessionKeys = new HashMap<>();
    private final Map<String, String> queuedText = new HashMap<>();

    private KeyPair localKeyPair;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences("liv_messenger", MODE_PRIVATE);
        username = prefs.getString("username", "").trim();
        connectionsClient = Nearby.getConnectionsClient(this);
        ensureKeyPair();
        buildHomeUi();

        if (username.isEmpty()) {
            showUsernameDialog(true);
        } else if (hasNearbyPermissions()) {
            startAdvertising();
        }
    }

    private void buildHomeUi() {
        inChat = false;
        activePeerName = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(18), dp(16), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("L", 24, true, BG);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(roundRect(ACCENT, ACCENT, 22, 0));
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        titleBox.addView(text("LIV Messenger", 27, true, TEXT));
        homeSubtitle = text(username.isEmpty() ? "Configure seu @usuário" : "@" + username + " • mensagens offline", 13, false, MUTED);
        titleBox.addView(homeSubtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button profile = smallButton("⚙");
        profile.setOnClickListener(v -> showUsernameDialog(false));
        header.addView(profile, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(header);

        LinearLayout secureBar = new LinearLayout(this);
        secureBar.setPadding(dp(12), dp(9), dp(12), dp(9));
        secureBar.setGravity(Gravity.CENTER_VERTICAL);
        secureBar.setBackground(roundRect(SURFACE, SURFACE_2, 14, 1));
        TextView secureText = text("●  OFFLINE FIRST  •  SESSÃO CRIPTOGRAFADA", 12, true, GREEN);
        secureBar.addView(secureText);
        LinearLayout.LayoutParams secureLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secureLp.topMargin = dp(16);
        root.addView(secureBar, secureLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(14), 0, dp(8));
        conversationsTab = tabButton("CONVERSAS", !showNearby);
        nearbyTab = tabButton("PRÓXIMOS", showNearby);
        conversationsTab.setOnClickListener(v -> {
            showNearby = false;
            buildHomeUi();
        });
        nearbyTab.setOnClickListener(v -> {
            showNearby = true;
            buildHomeUi();
            ensurePermissionsAndDiscover();
        });
        tabs.addView(conversationsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams tab2 = new LinearLayout.LayoutParams(0, dp(48), 1);
        tab2.leftMargin = dp(8);
        tabs.addView(nearbyTab, tab2);
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        contentList = new LinearLayout(this);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentList.setPadding(0, dp(4), 0, dp(12));
        scroll.addView(contentList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomAction = primaryButton(showNearby ? "BUSCAR NOVAMENTE" : "+ NOVA CONVERSA");
        bottomAction.setOnClickListener(v -> {
            showNearby = true;
            buildHomeUi();
            ensurePermissionsAndDiscover();
        });
        root.addView(bottomAction, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView footer = text("LIV 0.3.0 • sem QR • sem número de telefone", 11, false, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(13), 0, 0);
        root.addView(footer);

        setContentView(root);
        if (showNearby) refreshNearbyList(); else refreshConversationList();
    }

    private void refreshConversationList() {
        if (contentList == null) return;
        contentList.removeAllViews();

        List<String> peers = new ArrayList<>(prefs.getStringSet("peers", Collections.emptySet()));
        peers.sort((a, b) -> Long.compare(lastTimestamp(b), lastTimestamp(a)));

        if (peers.isEmpty()) {
            LinearLayout empty = card();
            TextView title = text("Suas conversas vão aparecer aqui", 20, true, TEXT);
            TextView body = text("Toque em Nova conversa. O LIV procura outros usuários próximos usando os rádios do celular, sem QR Code.", 14, false, MUTED);
            body.setPadding(0, dp(8), 0, 0);
            empty.addView(title);
            empty.addView(body);
            contentList.addView(empty, lpTop(8));
            return;
        }

        for (String peer : peers) {
            JSONObject last = lastMessage(peer);
            String preview = last == null ? "Conversa iniciada" : last.optString("text", "Mensagem");
            long ts = last == null ? 0 : last.optLong("ts", 0);
            boolean online = isPeerConnected(peer);

            LinearLayout item = card();
            item.setClickable(true);
            item.setOnClickListener(v -> openChat(peer));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView avatar = text(initials(peer), 16, true, BG);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(roundRect(online ? ACCENT : Color.rgb(102, 124, 140), Color.TRANSPARENT, 26, 0));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(12), 0, dp(8), 0);
            mid.addView(text("@" + stripAt(peer), 17, true, TEXT));
            TextView pv = text(preview, 14, false, MUTED);
            pv.setMaxLines(1);
            mid.addView(pv);
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(text(ts == 0 ? "" : shortTime(ts), 12, false, MUTED));
            TextView state = text(online ? "● próximo" : "offline", 12, true, online ? GREEN : MUTED);
            state.setPadding(0, dp(6), 0, 0);
            right.addView(state);
            row.addView(right);

            item.addView(row);
            contentList.addView(item, lpTop(8));
        }
    }

    private void refreshNearbyList() {
        if (contentList == null) return;
        contentList.removeAllViews();

        LinearLayout intro = card();
        intro.addView(text("Pessoas próximas", 20, true, TEXT));
        TextView hint = text("Deixe o LIV aberto nos dois celulares. Quando um usuário aparecer, toque para conversar.", 14, false, MUTED);
        hint.setPadding(0, dp(7), 0, 0);
        intro.addView(hint);
        contentList.addView(intro, lpTop(8));

        if (discovered.isEmpty()) {
            TextView searching = text("Procurando usuários LIV próximos…", 15, false, MUTED);
            searching.setGravity(Gravity.CENTER);
            searching.setPadding(0, dp(28), 0, dp(28));
            contentList.addView(searching);
            return;
        }

        for (Map.Entry<String, String> e : discovered.entrySet()) {
            String endpointId = e.getKey();
            String peer = e.getValue();

            LinearLayout item = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView avatar = text(initials(peer), 16, true, BG);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(roundRect(ACCENT, Color.TRANSPARENT, 26, 0));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(12), 0, dp(8), 0);
            mid.addView(text("@" + stripAt(peer), 17, true, TEXT));
            mid.addView(text(connectedEndpoints.contains(endpointId) ? "Conectado agora" : "LIV disponível", 13, false, connectedEndpoints.contains(endpointId) ? GREEN : MUTED));
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button talk = compactButton(connectedEndpoints.contains(endpointId) ? "ABRIR" : "CONVERSAR");
            talk.setOnClickListener(v -> {
                if (connectedEndpoints.contains(endpointId)) {
                    openChat(peer);
                } else {
                    connectTo(endpointId, peer);
                }
            });
            row.addView(talk, new LinearLayout.LayoutParams(dp(118), dp(46)));
            item.addView(row);
            contentList.addView(item, lpTop(8));
        }
    }

    private void openChat(String peer) {
        inChat = true;
        activePeerName = peer;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(14), dp(12), dp(12));
        top.setBackgroundColor(SURFACE);

        Button back = smallButton("‹");
        back.setTextSize(30);
        back.setOnClickListener(v -> {
            inChat = false;
            buildHomeUi();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(8), 0, 0, 0);
        titleBox.addView(text("@" + stripAt(peer), 19, true, TEXT));
        chatStatus = text(isPeerConnected(peer) ? "● próximo • protegido" : "offline • mensagem não será enviada agora", 12, false, isPeerConnected(peer) ? GREEN : MUTED);
        titleBox.addView(chatStatus);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        messagesList.setPadding(dp(12), dp(16), dp(12), dp(16));
        messagesScroll.addView(messagesList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(messagesScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setGravity(Gravity.BOTTOM);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(12));
        inputBar.setBackgroundColor(SURFACE);

        messageInput = new EditText(this);
        messageInput.setHint("Mensagem");
        messageInput.setHintTextColor(MUTED);
        messageInput.setTextColor(TEXT);
        messageInput.setTextSize(16);
        messageInput.setMinHeight(dp(50));
        messageInput.setMaxLines(4);
        messageInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        messageInput.setBackground(roundRect(SURFACE_2, Color.rgb(47, 65, 78), 24, 1));
        inputBar.addView(messageInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button send = primaryButton("➤");
        send.setTextSize(22);
        send.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(58), dp(52));
        sendLp.leftMargin = dp(8);
        inputBar.addView(send, sendLp);
        root.addView(inputBar);

        setContentView(root);
        renderMessages(peer);
    }

    private void renderMessages(String peer) {
        if (messagesList == null) return;
        messagesList.removeAllViews();
        JSONArray arr = loadMessages(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            addMessageBubble(o.optString("text", ""), o.optBoolean("mine", false), o.optLong("ts", 0));
        }
        scrollMessagesToBottom();
    }

    private void addMessageBubble(String message, boolean mine, long ts) {
        if (messagesList == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(8));
        bubble.setBackground(roundRect(mine ? Color.rgb(25, 91, 86) : SURFACE_2, Color.TRANSPARENT, 18, 0));

        TextView body = text(message, 16, false, TEXT);
        bubble.addView(body);
        TextView time = text(shortTime(ts), 11, false, mine ? Color.rgb(183, 226, 222) : MUTED);
        time.setGravity(Gravity.END);
        time.setPadding(dp(16), dp(4), 0, 0);
        bubble.addView(time);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.width = Math.min(getResources().getDisplayMetrics().widthPixels * 78 / 100, dp(340));
        row.addView(bubble, bp);
        messagesList.addView(row);
    }

    private void sendCurrentMessage() {
        if (messageInput == null || activePeerName == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        String endpointId = nameToEndpoint.get(activePeerName);
        if (endpointId == null || !connectedEndpoints.contains(endpointId)) {
            Toast.makeText(this, "@" + stripAt(activePeerName) + " não está próximo agora.", Toast.LENGTH_LONG).show();
            return;
        }

        messageInput.setText("");
        SecretKey key = sessionKeys.get(endpointId);
        if (key == null) {
            queuedText.put(endpointId, text);
            sendPublicKey(endpointId);
            Toast.makeText(this, "Preparando sessão criptografada…", Toast.LENGTH_SHORT).show();
            return;
        }
        sendEncryptedText(endpointId, activePeerName, text);
    }

    private void sendEncryptedText(String endpointId, String peer, String text) {
        try {
            SecretKey key = sessionKeys.get(endpointId);
            if (key == null) return;
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            long ts = System.currentTimeMillis();
            String packet = "MSG|" + ts + "|" + Base64.encodeToString(iv, Base64.NO_WRAP) + "|" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet.getBytes(StandardCharsets.UTF_8)))
                    .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Falha ao enviar mensagem.", Toast.LENGTH_LONG).show()));
            saveMessage(peer, text, true, ts);
            if (inChat && peer.equals(activePeerName)) {
                addMessageBubble(text, true, ts);
                scrollMessagesToBottom();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro na criptografia da mensagem.", Toast.LENGTH_LONG).show();
        }
    }

    private void connectTo(String endpointId, String peer) {
        if (username.isEmpty()) {
            showUsernameDialog(true);
            return;
        }
        pendingChatEndpoint = endpointId;
        pendingNames.put(endpointId, peer);
        Toast.makeText(this, "Conectando a @" + stripAt(peer) + "…", Toast.LENGTH_SHORT).show();
        connectionsClient.requestConnection("@" + username, endpointId, connectionLifecycleCallback)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Não foi possível iniciar a conexão.", Toast.LENGTH_LONG).show()));
    }

    private void ensurePermissionsAndDiscover() {
        if (username.isEmpty()) {
            showUsernameDialog(true);
            return;
        }
        if (!hasNearbyPermissions()) {
            pendingDiscoverAfterPermission = true;
            requestPermissions(requiredNearbyPermissions(), REQ_NEARBY);
            return;
        }
        startAdvertising();
        startDiscovery();
    }

    private String[] requiredNearbyPermissions() {
        List<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_SCAN);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33) {
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

    private void startAdvertising() {
        if (!hasNearbyPermissions() || username.isEmpty()) return;
        connectionsClient.stopAdvertising();
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising("@" + username, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Não foi possível ficar visível para outros LIV.", Toast.LENGTH_LONG).show()));
    }

    private void startDiscovery() {
        if (!hasNearbyPermissions()) return;
        discovered.clear();
        if (showNearby) refreshNearbyList();
        connectionsClient.stopDiscovery();
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> runOnUiThread(() -> {
                    if (bottomAction != null) bottomAction.setText("BUSCANDO…");
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    if (bottomAction != null) bottomAction.setText("BUSCAR NOVAMENTE");
                    Toast.makeText(this, "Falha ao procurar aparelhos próximos.", Toast.LENGTH_LONG).show();
                }));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            if (peer.equals("@" + username)) return;
            discovered.put(endpointId, peer);
            endpointToName.put(endpointId, peer);
            runOnUiThread(() -> {
                if (showNearby && !inChat) refreshNearbyList();
                if (bottomAction != null) bottomAction.setText("BUSCAR NOVAMENTE");
            });
        }

        @Override
        public void onEndpointLost(String endpointId) {
            discovered.remove(endpointId);
            runOnUiThread(() -> {
                if (showNearby && !inChat) refreshNearbyList();
            });
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            pendingNames.put(endpointId, peer);
            runOnUiThread(() -> showConnectionApproval(endpointId, peer, info.getAuthenticationDigits()));
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            runOnUiThread(() -> {
                if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                    String peer = pendingNames.getOrDefault(endpointId, endpointToName.getOrDefault(endpointId, "@usuario"));
                    peer = normalizePeer(peer);
                    connectedEndpoints.add(endpointId);
                    endpointToName.put(endpointId, peer);
                    nameToEndpoint.put(peer, endpointId);
                    addPeer(peer);
                    sendPublicKey(endpointId);
                    Toast.makeText(this, "Conectado a @" + stripAt(peer), Toast.LENGTH_SHORT).show();
                    if (pendingChatEndpoint != null && pendingChatEndpoint.equals(endpointId)) {
                        pendingChatEndpoint = null;
                        openChat(peer);
                    } else if (!inChat) {
                        if (showNearby) refreshNearbyList(); else refreshConversationList();
                    }
                } else {
                    Toast.makeText(this, "Conexão não concluída. Código: " + result.getStatus().getStatusCode(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onDisconnected(String endpointId) {
            String peer = endpointToName.get(endpointId);
            connectedEndpoints.remove(endpointId);
            sessionKeys.remove(endpointId);
            if (peer != null) nameToEndpoint.remove(peer);
            runOnUiThread(() -> {
                if (inChat && peer != null && peer.equals(activePeerName) && chatStatus != null) {
                    chatStatus.setText("offline • mensagem não será enviada agora");
                    chatStatus.setTextColor(MUTED);
                } else if (!inChat) {
                    if (showNearby) refreshNearbyList(); else refreshConversationList();
                }
            });
        }
    };

    private void showConnectionApproval(String endpointId, String peer, String digits) {
        new AlertDialog.Builder(this)
                .setTitle("Conectar com @" + stripAt(peer) + "?")
                .setMessage("Compare este código nos dois celulares:\n\n" + digits + "\n\nSe os códigos forem iguais, aceite nos dois aparelhos.")
                .setPositiveButton("ACEITAR", (d, w) -> connectionsClient.acceptConnection(endpointId, payloadCallback))
                .setNegativeButton("RECUSAR", (d, w) -> connectionsClient.rejectConnection(endpointId))
                .setCancelable(false)
                .show();
    }

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            String packet = new String(payload.asBytes(), StandardCharsets.UTF_8);
            if (packet.startsWith("KEY|")) {
                handleRemoteKey(endpointId, packet.substring(4));
            } else if (packet.startsWith("MSG|")) {
                handleEncryptedMessage(endpointId, packet);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
        }
    };

    private void ensureKeyPair() {
        if (localKeyPair != null) return;
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            localKeyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao iniciar criptografia LIV", e);
        }
    }

    private void sendPublicKey(String endpointId) {
        ensureKeyPair();
        String publicKey = Base64.encodeToString(localKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
        String packet = "KEY|" + publicKey;
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet.getBytes(StandardCharsets.UTF_8)));
    }

    private void handleRemoteKey(String endpointId, String base64Key) {
        try {
            byte[] encoded = Base64.decode(base64Key, Base64.NO_WRAP);
            PublicKey remote = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(localKeyPair.getPrivate());
            agreement.doPhase(remote, true);
            byte[] shared = agreement.generateSecret();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(shared);
            SecretKey key = new SecretKeySpec(digest, "AES");
            sessionKeys.put(endpointId, key);

            String queued = queuedText.remove(endpointId);
            String peer = endpointToName.get(endpointId);
            runOnUiThread(() -> {
                if (inChat && peer != null && peer.equals(activePeerName) && chatStatus != null) {
                    chatStatus.setText("● próximo • criptografia ativa");
                    chatStatus.setTextColor(GREEN);
                }
                if (queued != null && peer != null) sendEncryptedText(endpointId, peer, queued);
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Não foi possível criar a sessão criptografada.", Toast.LENGTH_LONG).show());
        }
    }

    private void handleEncryptedMessage(String endpointId, String packet) {
        try {
            String[] parts = packet.split("\\|", 4);
            if (parts.length != 4) return;
            long ts = Long.parseLong(parts[1]);
            SecretKey key = sessionKeys.get(endpointId);
            if (key == null) {
                sendPublicKey(endpointId);
                return;
            }
            byte[] iv = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[3], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String text = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            String peer = endpointToName.getOrDefault(endpointId, "@usuario");
            saveMessage(peer, text, false, ts);

            runOnUiThread(() -> {
                if (inChat && peer.equals(activePeerName)) {
                    addMessageBubble(text, false, ts);
                    scrollMessagesToBottom();
                } else {
                    Toast.makeText(this, "Nova mensagem de @" + stripAt(peer), Toast.LENGTH_SHORT).show();
                    if (!showNearby) refreshConversationList();
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Mensagem recebida, mas não pôde ser descriptografada.", Toast.LENGTH_LONG).show());
        }
    }

    private void showUsernameDialog(boolean required) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(6), dp(22), 0);

        TextView info = text("Seu usuário identifica você no LIV. Não usamos número de telefone.", 14, false, Color.DKGRAY);
        box.addView(info);

        EditText input = new EditText(this);
        input.setHint("usuario");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(username);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(required ? "Criar seu @usuário" : "Perfil LIV")
                .setView(box)
                .setPositiveButton("SALVAR", null)
                .setNegativeButton(required ? null : "CANCELAR", null)
                .setCancelable(!required)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("@")) value = value.substring(1);
            if (!value.matches("[a-z0-9._]{3,20}")) {
                input.setError("Use 3 a 20 caracteres: letras, números, ponto ou _");
                return;
            }
            username = value;
            prefs.edit().putString("username", username).apply();
            dialog.dismiss();
            buildHomeUi();
            if (hasNearbyPermissions()) startAdvertising();
        }));
        dialog.show();
    }

    private void saveMessage(String peer, String text, boolean mine, long ts) {
        try {
            JSONArray arr = loadMessages(peer);
            JSONObject o = new JSONObject();
            o.put("text", text);
            o.put("mine", mine);
            o.put("ts", ts);
            arr.put(o);
            prefs.edit().putString(chatKey(peer), arr.toString()).apply();
            addPeer(peer);
        } catch (Exception ignored) {
        }
    }

    private JSONArray loadMessages(String peer) {
        try {
            return new JSONArray(prefs.getString(chatKey(peer), "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONObject lastMessage(String peer) {
        JSONArray arr = loadMessages(peer);
        if (arr.length() == 0) return null;
        return arr.optJSONObject(arr.length() - 1);
    }

    private long lastTimestamp(String peer) {
        JSONObject o = lastMessage(peer);
        return o == null ? 0 : o.optLong("ts", 0);
    }

    private void addPeer(String peer) {
        Set<String> peers = new HashSet<>(prefs.getStringSet("peers", Collections.emptySet()));
        peers.add(normalizePeer(peer));
        prefs.edit().putStringSet("peers", peers).apply();
    }

    private String chatKey(String peer) {
        return "chat_" + Base64.encodeToString(normalizePeer(peer).getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private boolean isPeerConnected(String peer) {
        String endpoint = nameToEndpoint.get(normalizePeer(peer));
        return endpoint != null && connectedEndpoints.contains(endpoint);
    }

    private String normalizePeer(String name) {
        if (name == null || name.trim().isEmpty()) return "@usuario";
        String n = name.trim();
        if (!n.startsWith("@")) n = "@" + n;
        return n;
    }

    private String stripAt(String name) {
        String n = normalizePeer(name);
        return n.substring(1);
    }

    private String initials(String peer) {
        String n = stripAt(peer);
        if (n.isEmpty()) return "L";
        return n.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String shortTime(long ts) {
        if (ts <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private void scrollMessagesToBottom() {
        if (messagesScroll != null) messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NEARBY) {
            if (hasNearbyPermissions()) {
                startAdvertising();
                if (pendingDiscoverAfterPermission) {
                    pendingDiscoverAfterPermission = false;
                    startDiscovery();
                }
            } else {
                Toast.makeText(this, "O LIV precisa da permissão Aparelhos próximos para conversar offline.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (inChat) {
            inChat = false;
            buildHomeUi();
        } else if (showNearby) {
            showNearby = false;
            buildHomeUi();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectionsClient != null) {
            connectionsClient.stopDiscovery();
            connectionsClient.stopAdvertising();
            connectionsClient.stopAllEndpoints();
        }
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(BG);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(ACCENT, ACCENT, 18, 0));
        return b;
    }

    private Button compactButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(ACCENT);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(SURFACE_2, ACCENT, 15, 1));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(20);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundRect(SURFACE_2, Color.rgb(52, 70, 84), 18, 1));
        return b;
    }

    private Button tabButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(active ? BG : MUTED);
        b.setAllCaps(false);
        b.setBackground(roundRect(active ? ACCENT : SURFACE, active ? ACCENT : SURFACE_2, 15, 1));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(roundRect(SURFACE, Color.rgb(38, 58, 73), 18, 1));
        return c;
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
