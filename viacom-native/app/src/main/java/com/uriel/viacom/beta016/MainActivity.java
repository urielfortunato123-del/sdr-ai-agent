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
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    private static final String SERVICE_ID = "com.uriel.livmessenger.offline.v2";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final int REQ_NEARBY = 900;

    private final int BG = Color.rgb(17, 27, 33);
    private final int BAR = Color.rgb(31, 44, 52);
    private final int INPUT = Color.rgb(42, 57, 66);
    private final int GREEN = Color.rgb(0, 168, 132);
    private final int TEXT = Color.rgb(238, 242, 244);
    private final int MUTED = Color.rgb(134, 150, 160);
    private final int LINE = Color.rgb(46, 61, 69);
    private final int BUBBLE_ME = Color.rgb(0, 92, 75);
    private final int BUBBLE_OTHER = Color.rgb(32, 44, 51);

    private ConnectionsClient nearby;
    private android.content.SharedPreferences prefs;
    private String username = "";
    private String activePeer = null;
    private String activeEndpoint = null;
    private boolean inChat = false;
    private boolean showingNearby = false;
    private boolean pendingDiscoverAfterPermission = false;

    private final Map<String, String> discovered = new LinkedHashMap<>();
    private final Map<String, String> endpointToPeer = new HashMap<>();
    private final Map<String, String> peerToEndpoint = new HashMap<>();
    private final Set<String> connected = new HashSet<>();
    private final Map<String, SecretKey> sessionKeys = new HashMap<>();
    private final Map<String, String> pendingNames = new HashMap<>();

    private KeyPair localKeyPair;
    private final SecureRandom random = new SecureRandom();

    private LinearLayout listHost;
    private TextView subtitleView;
    private ScrollView chatScroll;
    private LinearLayout messageHost;
    private EditText messageInput;
    private TextView chatStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 20, 25));
        getWindow().setNavigationBarColor(Color.rgb(11, 20, 25));

        prefs = getSharedPreferences("liv_messenger_v2", MODE_PRIVATE);
        username = prefs.getString("username", "").trim();
        nearby = Nearby.getConnectionsClient(this);
        ensureKeyPair();
        showHome();

        if (username.isEmpty()) {
            showProfileDialog(true);
        } else if (hasNearbyPermissions()) {
            startAdvertising();
        }
    }

    private void showHome() {
        inChat = false;
        activePeer = null;
        activeEndpoint = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(8), dp(8));
        top.setBackgroundColor(BAR);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("LIV", 28, true, TEXT);
        subtitleView = text(username.isEmpty() ? "Configure seu @usuário" : "@" + username, 12, false, MUTED);
        titleBox.addView(title);
        titleBox.addView(subtitleView);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button search = iconButton("⌕");
        search.setOnClickListener(v -> searchChats());
        top.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));

        Button menu = iconButton("⋮");
        menu.setTextSize(27);
        menu.setOnClickListener(v -> showMainMenu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(BAR);
        tabs.setPadding(dp(6), 0, dp(6), 0);
        tabs.addView(homeTab("CONVERSAS", !showingNearby, () -> { showingNearby = false; showHome(); }), new LinearLayout.LayoutParams(0, dp(50), 1));
        tabs.addView(homeTab("PRÓXIMOS", showingNearby, () -> { showingNearby = true; showHome(); ensurePermissionsAndDiscover(); }), new LinearLayout.LayoutParams(0, dp(50), 1));
        tabs.addView(homeTab("STATUS", false, () -> showFeature("Status", "Status offline e compartilhamento local entram na próxima etapa.")), new LinearLayout.LayoutParams(0, dp(50), 1));
        tabs.addView(homeTab("CHAMADAS", false, () -> showFeature("Chamadas", "Chamadas de voz e vídeo serão adicionadas depois do chat offline estabilizar.")), new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        listHost.setPadding(0, dp(6), 0, dp(90));
        scroll.addView(listHost, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(6), dp(16), dp(12));
        bottom.setBackgroundColor(BG);
        Button fab = new Button(this);
        fab.setText(showingNearby ? "↻" : "✎");
        fab.setTextSize(25);
        fab.setTextColor(Color.WHITE);
        fab.setAllCaps(false);
        fab.setBackground(roundRect(GREEN, GREEN, 18, 0));
        fab.setOnClickListener(v -> {
            showingNearby = true;
            showHome();
            ensurePermissionsAndDiscover();
        });
        bottom.addView(fab, new LinearLayout.LayoutParams(dp(62), dp(62)));
        root.addView(bottom);

        setContentView(root);
        if (showingNearby) refreshNearby(); else refreshChats(null);
    }

    private void refreshChats(String filter) {
        if (listHost == null) return;
        listHost.removeAllViews();

        List<String> peers = new ArrayList<>(prefs.getStringSet("peers", Collections.emptySet()));
        peers.sort((a, b) -> Long.compare(lastTimestamp(b), lastTimestamp(a)));

        if (peers.isEmpty()) {
            addEmptyState("Nenhuma conversa ainda", "Toque no botão verde para encontrar pessoas próximas e iniciar uma conversa sem internet.");
            return;
        }

        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        for (String peer : peers) {
            if (!f.isEmpty() && !peer.toLowerCase(Locale.ROOT).contains(f)) continue;
            JSONObject last = lastMessage(peer);
            String preview = last == null ? "Conversa iniciada" : last.optString("text", "Mensagem");
            long ts = last == null ? 0 : last.optLong("ts", 0);
            String state = last == null ? "" : last.optString("state", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(11), dp(14), dp(11));
            row.setClickable(true);
            row.setOnClickListener(v -> openChat(peer));

            TextView avatar = avatar(peer, isPeerConnected(peer));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(55), dp(55)));

            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(dp(14), 0, dp(8), 0);
            TextView name = text("@" + stripAt(peer), 17, true, TEXT);
            TextView pv = text(preview, 14, false, MUTED);
            pv.setMaxLines(1);
            middle.addView(name);
            middle.addView(pv);
            row.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(text(ts == 0 ? "" : shortTime(ts), 12, false, isPeerConnected(peer) ? GREEN : MUTED));
            String stateText = isPeerConnected(peer) ? "● próximo" : ("queued".equals(state) ? "◷ na fila" : "offline");
            TextView st = text(stateText, 11, false, "queued".equals(state) ? Color.rgb(240, 190, 70) : MUTED);
            st.setPadding(0, dp(5), 0, 0);
            right.addView(st);
            row.addView(right);

            listHost.addView(row);
            View sep = new View(this);
            sep.setBackgroundColor(LINE);
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            sepLp.leftMargin = dp(85);
            listHost.addView(sep, sepLp);
            shown++;
        }

        if (shown == 0) addEmptyState("Nenhuma conversa encontrada", "Tente outro nome de usuário.");
    }

    private void refreshNearby() {
        if (listHost == null) return;
        listHost.removeAllViews();

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), dp(16), dp(18), dp(12));
        info.addView(text("Pessoas próximas", 20, true, TEXT));
        info.addView(text("O LIV procura somente outros LIV próximos. Não usa QR Code nem número de telefone.", 13, false, MUTED));
        listHost.addView(info);

        if (discovered.isEmpty()) {
            TextView looking = text("Procurando usuários LIV…", 15, false, MUTED);
            looking.setGravity(Gravity.CENTER);
            looking.setPadding(0, dp(40), 0, dp(40));
            listHost.addView(looking);
            return;
        }

        for (Map.Entry<String, String> e : discovered.entrySet()) {
            String endpoint = e.getKey();
            String peer = normalizePeer(e.getValue());
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(12), dp(12));
            row.addView(avatar(peer, true), new LinearLayout.LayoutParams(dp(55), dp(55)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(14), 0, dp(8), 0);
            mid.addView(text("@" + stripAt(peer), 17, true, TEXT));
            mid.addView(text(connected.contains(endpoint) ? "Conectado" : "LIV disponível", 13, false, connected.contains(endpoint) ? GREEN : MUTED));
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button action = compactButton(connected.contains(endpoint) ? "ABRIR" : "CONVERSAR");
            action.setOnClickListener(v -> {
                if (connected.contains(endpoint)) openChat(peer);
                else connectTo(endpoint, peer);
            });
            row.addView(action, new LinearLayout.LayoutParams(dp(122), dp(44)));
            listHost.addView(row);
        }
    }

    private void openChat(String peer) {
        inChat = true;
        activePeer = normalizePeer(peer);
        activeEndpoint = peerToEndpoint.get(activePeer);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(8), dp(8));
        top.setBackgroundColor(BAR);

        Button back = iconButton("‹");
        back.setTextSize(32);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(48)));
        top.addView(avatar(activePeer, isPeerConnected(activePeer)), new LinearLayout.LayoutParams(dp(43), dp(43)));

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(dp(10), 0, 0, 0);
        who.addView(text("@" + stripAt(activePeer), 17, true, TEXT));
        chatStatus = text(isPeerConnected(activePeer) ? "próximo • criptografado" : "offline • mensagens ficam na fila", 12, false, isPeerConnected(activePeer) ? GREEN : MUTED);
        who.addView(chatStatus);
        top.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button call = iconButton("☎");
        call.setOnClickListener(v -> showFeature("Chamada LIV", "Chamadas entram depois do chat offline direto."));
        top.addView(call, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button more = iconButton("⋮");
        more.setOnClickListener(v -> showChatMenu(activePeer));
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messageHost = new LinearLayout(this);
        messageHost.setOrientation(LinearLayout.VERTICAL);
        messageHost.setPadding(dp(10), dp(16), dp(10), dp(12));
        chatScroll.addView(messageHost, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout compose = new LinearLayout(this);
        compose.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        compose.setPadding(dp(6), dp(6), dp(6), dp(8));
        compose.setBackgroundColor(BG);

        Button emoji = miniComposeButton("☺");
        emoji.setOnClickListener(v -> insertText("🙂"));
        compose.addView(emoji, new LinearLayout.LayoutParams(dp(46), dp(50)));

        Button attach = miniComposeButton("＋");
        attach.setOnClickListener(v -> showAttachmentMenu());
        compose.addView(attach, new LinearLayout.LayoutParams(dp(46), dp(50)));

        messageInput = new EditText(this);
        messageInput.setHint("Mensagem");
        messageInput.setHintTextColor(MUTED);
        messageInput.setTextColor(TEXT);
        messageInput.setTextSize(16);
        messageInput.setMaxLines(5);
        messageInput.setPadding(dp(15), dp(10), dp(15), dp(10));
        messageInput.setBackground(roundRect(INPUT, INPUT, 24, 0));
        compose.addView(messageInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button send = new Button(this);
        send.setText("➤");
        send.setTextSize(20);
        send.setTextColor(Color.WHITE);
        send.setAllCaps(false);
        send.setBackground(roundRect(GREEN, GREEN, 26, 0));
        send.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        sendLp.leftMargin = dp(6);
        compose.addView(send, sendLp);
        root.addView(compose);

        setContentView(root);
        renderMessages(activePeer);
        markPeerRead(activePeer);
        scrollBottom();
    }

    private void renderMessages(String peer) {
        if (messageHost == null) return;
        messageHost.removeAllViews();

        TextView banner = text("Mensagens e chamadas são protegidas nesta sessão direta. O LIV não usa número de telefone.", 11, false, Color.rgb(210, 190, 140));
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(18), dp(10), dp(18), dp(16));
        messageHost.addView(banner);

        JSONArray arr = history(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            addBubble(m.optString("text", ""), m.optBoolean("mine", false), m.optLong("ts", 0), m.optString("state", ""));
        }
    }

    private void addBubble(String value, boolean mine, long ts, String state) {
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(mine ? Gravity.END : Gravity.START);
        holder.setPadding(dp(3), dp(3), dp(3), dp(3));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(10), dp(6));
        bubble.setBackground(roundRect(mine ? BUBBLE_ME : BUBBLE_OTHER, mine ? BUBBLE_ME : BUBBLE_OTHER, 12, 0));
        TextView msg = text(value, 16, false, TEXT);
        bubble.addView(msg);

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        String time = ts == 0 ? "" : new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        meta.addView(text(time, 10, false, Color.rgb(186, 199, 205)));
        if (mine) {
            String ticks = "queued".equals(state) ? " ◷" : ("read".equals(state) ? "  ✓✓" : ("delivered".equals(state) ? "  ✓✓" : "  ✓"));
            TextView tick = text(ticks, 11, true, "read".equals(state) ? Color.rgb(83, 189, 235) : Color.rgb(190, 205, 210));
            meta.addView(tick);
        }
        bubble.addView(meta);

        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        b.width = dp(310);
        holder.addView(bubble, b);
        messageHost.addView(holder);
    }

    private void sendCurrentMessage() {
        if (messageInput == null || activePeer == null) return;
        String msg = messageInput.getText().toString().trim();
        if (msg.isEmpty()) return;
        messageInput.setText("");
        hideKeyboard();

        String endpoint = peerToEndpoint.get(activePeer);
        String id = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();

        if (endpoint == null || !connected.contains(endpoint)) {
            saveMessage(activePeer, id, msg, true, ts, "queued");
            addBubble(msg, true, ts, "queued");
            scrollBottom();
            Toast.makeText(MainActivity.this, "Mensagem na fila. O LIV enviará quando @" + stripAt(activePeer) + " estiver próximo.", Toast.LENGTH_LONG).show();
            return;
        }

        SecretKey key = sessionKeys.get(endpoint);
        if (key == null) {
            saveMessage(activePeer, id, msg, true, ts, "queued");
            sendPublicKey(endpoint);
            Toast.makeText(MainActivity.this, "Preparando sessão criptografada…", Toast.LENGTH_SHORT).show();
            renderMessages(activePeer);
            scrollBottom();
            return;
        }

        saveMessage(activePeer, id, msg, true, ts, "sent");
        sendEncrypted(endpoint, activePeer, id, msg, ts);
        renderMessages(activePeer);
        scrollBottom();
    }

    private void sendEncrypted(String endpoint, String peer, String id, String msg, long ts) {
        try {
            SecretKey key = sessionKeys.get(endpoint);
            if (key == null) return;
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = cipher.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            String packet = "MSG|" + id + "|" + ts + "|" + Base64.encodeToString(iv, Base64.NO_WRAP) + "|" + Base64.encodeToString(enc, Base64.NO_WRAP);
            nearby.sendPayload(endpoint, Payload.fromBytes(packet.getBytes(StandardCharsets.UTF_8)))
                    .addOnFailureListener(e -> runOnUiThread(() -> {
                        updateMessageState(peer, id, "queued");
                        if (inChat && peer.equals(activePeer)) renderMessages(peer);
                    }));
        } catch (Exception e) {
            updateMessageState(peer, id, "queued");
        }
    }

    private void flushQueued(String peer) {
        String endpoint = peerToEndpoint.get(peer);
        if (endpoint == null || !connected.contains(endpoint) || sessionKeys.get(endpoint) == null) return;
        JSONArray arr = history(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null || !m.optBoolean("mine", false)) continue;
            if (!"queued".equals(m.optString("state", ""))) continue;
            String id = m.optString("id", UUID.randomUUID().toString());
            long ts = m.optLong("ts", System.currentTimeMillis());
            updateMessageState(peer, id, "sent");
            sendEncrypted(endpoint, peer, id, m.optString("text", ""), ts);
        }
        if (inChat && peer.equals(activePeer)) renderMessages(peer);
    }

    private void ensurePermissionsAndDiscover() {
        if (username.isEmpty()) {
            showProfileDialog(true);
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

    private void startAdvertising() {
        if (username.isEmpty() || !hasNearbyPermissions()) return;
        nearby.stopAdvertising();
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        nearby.startAdvertising("@" + username, SERVICE_ID, connectionLifecycle, options)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível deixar o LIV visível.", Toast.LENGTH_LONG).show()));
    }

    private void startDiscovery() {
        if (!hasNearbyPermissions()) return;
        discovered.clear();
        if (showingNearby) refreshNearby();
        nearby.stopDiscovery();
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        nearby.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Falha ao procurar usuários próximos.", Toast.LENGTH_LONG).show()));
    }

    private void connectTo(String endpoint, String peer) {
        pendingNames.put(endpoint, normalizePeer(peer));
        nearby.requestConnection("@" + username, endpoint, connectionLifecycle)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível iniciar a conexão.", Toast.LENGTH_LONG).show()));
    }

    private final EndpointDiscoveryCallback discoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            if (peer.equals(normalizePeer(username))) return;
            discovered.put(endpointId, peer);
            endpointToPeer.put(endpointId, peer);
            runOnUiThread(() -> {
                if (showingNearby) refreshNearby();
                if (hasQueued(peer) && !connected.contains(endpointId)) connectTo(endpointId, peer);
            });
        }

        @Override
        public void onEndpointLost(String endpointId) {
            discovered.remove(endpointId);
            runOnUiThread(() -> { if (showingNearby) refreshNearby(); });
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycle = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            pendingNames.put(endpointId, peer);
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Conectar com @" + stripAt(peer))
                        .setMessage("Compare este código nos dois celulares:\n\n" + info.getAuthenticationDigits() + "\n\nAceite somente se os códigos forem iguais.")
                        .setNegativeButton("RECUSAR", (d, w) -> nearby.rejectConnection(endpointId))
                        .setPositiveButton("ACEITAR", (d, w) -> nearby.acceptConnection(endpointId, payloadCallback))
                        .setCancelable(false)
                        .show();
            });
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            runOnUiThread(() -> {
                if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                    String peer = pendingNames.getOrDefault(endpointId, endpointToPeer.getOrDefault(endpointId, "@usuario"));
                    peer = normalizePeer(peer);
                    connected.add(endpointId);
                    endpointToPeer.put(endpointId, peer);
                    peerToEndpoint.put(peer, endpointId);
                    addPeer(peer);
                    sendPublicKey(endpointId);
                    if (showingNearby) refreshNearby();
                    if (inChat && peer.equals(activePeer) && chatStatus != null) {
                        chatStatus.setText("próximo • criptografado");
                        chatStatus.setTextColor(GREEN);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Conexão não concluída.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onDisconnected(String endpointId) {
            String peer = endpointToPeer.get(endpointId);
            connected.remove(endpointId);
            sessionKeys.remove(endpointId);
            if (peer != null) peerToEndpoint.remove(peer);
            runOnUiThread(() -> {
                if (inChat && peer != null && peer.equals(activePeer) && chatStatus != null) {
                    chatStatus.setText("offline • mensagens ficam na fila");
                    chatStatus.setTextColor(MUTED);
                }
                if (!inChat && !showingNearby) refreshChats(null);
            });
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            String packet = new String(payload.asBytes(), StandardCharsets.UTF_8);
            if (packet.startsWith("KEY|")) handlePublicKey(endpointId, packet.substring(4));
            else if (packet.startsWith("MSG|")) handleMessage(endpointId, packet);
            else if (packet.startsWith("ACK|")) handleAck(endpointId, packet.substring(4), "delivered");
            else if (packet.startsWith("READ|")) handleAck(endpointId, packet.substring(5), "read");
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) { }
    };

    private void sendPublicKey(String endpointId) {
        try {
            String pub = Base64.encodeToString(localKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
            nearby.sendPayload(endpointId, Payload.fromBytes(("KEY|" + pub).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) { }
    }

    private void handlePublicKey(String endpointId, String value) {
        try {
            byte[] encoded = Base64.decode(value, Base64.NO_WRAP);
            PublicKey remote = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(localKeyPair.getPrivate());
            agreement.doPhase(remote, true);
            byte[] shared = agreement.generateSecret();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(shared);
            sessionKeys.put(endpointId, new SecretKeySpec(digest, "AES"));
            String peer = endpointToPeer.get(endpointId);
            if (peer != null) {
                peerToEndpoint.put(peer, endpointId);
                runOnUiThread(() -> flushQueued(peer));
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível criar a sessão criptografada.", Toast.LENGTH_LONG).show());
        }
    }

    private void handleMessage(String endpointId, String packet) {
        try {
            String[] p = packet.split("\\|", 5);
            if (p.length != 5) return;
            String id = p[1];
            long ts = Long.parseLong(p[2]);
            byte[] iv = Base64.decode(p[3], Base64.NO_WRAP);
            byte[] enc = Base64.decode(p[4], Base64.NO_WRAP);
            SecretKey key = sessionKeys.get(endpointId);
            if (key == null) { sendPublicKey(endpointId); return; }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String msg = new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
            String peer = endpointToPeer.getOrDefault(endpointId, "@usuario");
            addPeer(peer);
            saveMessage(peer, id, msg, false, ts, "received");
            nearby.sendPayload(endpointId, Payload.fromBytes(("ACK|" + id).getBytes(StandardCharsets.UTF_8)));
            if (inChat && peer.equals(activePeer)) nearby.sendPayload(endpointId, Payload.fromBytes(("READ|" + id).getBytes(StandardCharsets.UTF_8)));
            runOnUiThread(() -> {
                if (inChat && peer.equals(activePeer)) {
                    renderMessages(peer);
                    scrollBottom();
                } else {
                    Toast.makeText(MainActivity.this, "Nova mensagem de @" + stripAt(peer), Toast.LENGTH_SHORT).show();
                    if (!showingNearby) refreshChats(null);
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Mensagem recebida, mas não pôde ser aberta.", Toast.LENGTH_LONG).show());
        }
    }

    private void handleAck(String endpointId, String id, String state) {
        String peer = endpointToPeer.get(endpointId);
        if (peer == null) return;
        updateMessageState(peer, id, state);
        runOnUiThread(() -> {
            if (inChat && peer.equals(activePeer)) renderMessages(peer);
            else if (!showingNearby) refreshChats(null);
        });
    }

    private void markPeerRead(String peer) {
        String endpoint = peerToEndpoint.get(peer);
        if (endpoint == null || !connected.contains(endpoint)) return;
        JSONArray arr = history(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null || m.optBoolean("mine", false)) continue;
            String id = m.optString("id", "");
            if (!id.isEmpty()) nearby.sendPayload(endpoint, Payload.fromBytes(("READ|" + id).getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void ensureKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            localKeyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void showProfileDialog(boolean required) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText user = new EditText(this);
        user.setHint("@usuário");
        user.setSingleLine(true);
        user.setText(username);
        user.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(user);
        EditText pass = new EditText(this);
        pass.setHint("Senha local da Beta");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(required ? "Criar seu perfil LIV" : "Perfil LIV")
                .setMessage("Nesta Beta o @usuário é local. O cadastro online único e o backup Google entram na próxima etapa.")
                .setView(box)
                .setNegativeButton(required ? "SAIR" : "CANCELAR", (d, w) -> { if (required) finish(); })
                .setPositiveButton("SALVAR", null)
                .setCancelable(!required)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = sanitizeUser(user.getText().toString());
            String pw = pass.getText().toString();
            if (u.length() < 3) { user.setError("Use pelo menos 3 caracteres"); return; }
            if (required && pw.length() < 4) { pass.setError("Use pelo menos 4 caracteres nesta Beta"); return; }
            username = u;
            prefs.edit().putString("username", username).apply();
            if (!pw.isEmpty()) prefs.edit().putString("password_hash", sha256(pw)).apply();
            dialog.dismiss();
            showHome();
            if (hasNearbyPermissions()) startAdvertising();
        }));
        dialog.show();
    }

    private void showMainMenu() {
        String[] items = {"Perfil", "Privacidade e segurança", "Backup", "Aparelhos próximos", "Sobre o LIV"};
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showProfileDialog(false);
            else if (which == 1) showFeature("Privacidade e segurança", "As mensagens diretas desta Beta usam ECDH para acordo de chave e AES-GCM para conteúdo. A identidade de longo prazo será endurecida antes da versão pública.");
            else if (which == 2) showFeature("Backup", "Planejado: backup criptografado no Google Drive somente quando houver Wi-Fi, sem expor suas mensagens ao provedor.");
            else if (which == 3) { showingNearby = true; showHome(); ensurePermissionsAndDiscover(); }
            else showFeature("LIV Messenger 0.4.0", "Mensageiro offline-first. Sem número de telefone, sem QR Code e com transporte direto entre aparelhos próximos.");
        }).show();
    }

    private void showChatMenu(String peer) {
        String[] items = {"Ver perfil", "Pesquisar na conversa", "Limpar conversa", "Segurança"};
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showFeature("@" + stripAt(peer), isPeerConnected(peer) ? "Usuário próximo agora." : "Usuário salvo. Fora de alcance agora.");
            else if (which == 1) searchConversation(peer);
            else if (which == 2) new AlertDialog.Builder(this).setTitle("Limpar conversa?").setMessage("As mensagens locais desta conversa serão apagadas.").setNegativeButton("CANCELAR", null).setPositiveButton("LIMPAR", (x, y) -> { prefs.edit().remove("history_" + key(peer)).apply(); renderMessages(peer); }).show();
            else showFeature("Segurança", isPeerConnected(peer) ? "Sessão direta criptografada ativa." : "Uma nova sessão criptografada será criada quando os aparelhos se encontrarem novamente.");
        }).show();
    }

    private void showAttachmentMenu() {
        String[] items = {"Câmera", "Galeria", "Documento", "Localização", "Contato", "Áudio"};
        new AlertDialog.Builder(this).setTitle("Anexar").setItems(items, (d, which) -> showFeature(items[which], "A interface já está preparada. O transporte desse tipo de arquivo entra na próxima build da mesma base LIV.")).show();
    }

    private void searchChats() {
        final EditText q = new EditText(this);
        q.setHint("Pesquisar conversas");
        q.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Pesquisar").setView(q).setNegativeButton("CANCELAR", null).setPositiveButton("BUSCAR", (d, w) -> { showingNearby = false; showHome(); refreshChats(q.getText().toString()); }).show();
    }

    private void searchConversation(String peer) {
        EditText q = new EditText(this);
        q.setHint("Texto da mensagem");
        q.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Pesquisar em @" + stripAt(peer)).setView(q).setNegativeButton("CANCELAR", null).setPositiveButton("BUSCAR", (d, w) -> {
            String needle = q.getText().toString().toLowerCase(Locale.ROOT).trim();
            if (needle.isEmpty()) return;
            JSONArray arr = history(peer);
            List<String> found = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m != null && m.optString("text", "").toLowerCase(Locale.ROOT).contains(needle)) found.add(m.optString("text", ""));
            }
            showFeature("Resultado", found.isEmpty() ? "Nenhuma mensagem encontrada." : String.join("\n\n", found.subList(0, Math.min(found.size(), 8))));
        }).show();
    }

    private void showFeature(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void addEmptyState(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(34), dp(70), dp(34), dp(34));
        TextView icon = text("L", 38, true, GREEN);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView t = text(title, 20, true, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, dp(8));
        box.addView(t);
        TextView b = text(body, 14, false, MUTED);
        b.setGravity(Gravity.CENTER);
        box.addView(b);
        listHost.addView(box);
    }

    private Button homeTab(String label, boolean selected, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(selected ? GREEN : MUTED);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundRect(selected ? Color.rgb(36, 54, 61) : BAR, selected ? GREEN : BAR, 0, selected ? 2 : 0));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private TextView avatar(String peer, boolean online) {
        TextView v = text(initials(peer), 17, true, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(online ? GREEN : Color.rgb(83, 101, 112), Color.TRANSPARENT, 30, 0));
        return v;
    }

    private Button compactButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(GREEN, GREEN, 12, 0));
        return b;
    }

    private Button iconButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(22);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private Button miniComposeButton(String label) {
        Button b = iconButton(label);
        b.setTextColor(MUTED);
        b.setTextSize(22);
        return b;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private void insertText(String s) {
        if (messageInput == null) return;
        int start = Math.max(messageInput.getSelectionStart(), 0);
        messageInput.getText().insert(start, s);
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void scrollBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addPeer(String peer) {
        Set<String> s = new HashSet<>(prefs.getStringSet("peers", Collections.emptySet()));
        s.add(normalizePeer(peer));
        prefs.edit().putStringSet("peers", s).apply();
    }

    private JSONArray history(String peer) {
        try { return new JSONArray(prefs.getString("history_" + key(peer), "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveHistory(String peer, JSONArray arr) {
        prefs.edit().putString("history_" + key(peer), arr.toString()).apply();
        addPeer(peer);
    }

    private void saveMessage(String peer, String id, String text, boolean mine, long ts, String state) {
        try {
            JSONArray arr = history(peer);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject old = arr.optJSONObject(i);
                if (old != null && id.equals(old.optString("id", ""))) return;
            }
            JSONObject m = new JSONObject();
            m.put("id", id); m.put("text", text); m.put("mine", mine); m.put("ts", ts); m.put("state", state);
            arr.put(m);
            saveHistory(peer, arr);
        } catch (Exception ignored) { }
    }

    private void updateMessageState(String peer, String id, String state) {
        try {
            JSONArray arr = history(peer);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m != null && id.equals(m.optString("id", ""))) {
                    String old = m.optString("state", "");
                    if (rankState(state) >= rankState(old)) m.put("state", state);
                    break;
                }
            }
            saveHistory(peer, arr);
        } catch (Exception ignored) { }
    }

    private int rankState(String s) {
        if ("read".equals(s)) return 4;
        if ("delivered".equals(s)) return 3;
        if ("sent".equals(s)) return 2;
        if ("queued".equals(s)) return 1;
        return 0;
    }

    private boolean hasQueued(String peer) {
        JSONArray arr = history(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m != null && m.optBoolean("mine", false) && "queued".equals(m.optString("state", ""))) return true;
        }
        return false;
    }

    private JSONObject lastMessage(String peer) {
        JSONArray arr = history(peer);
        return arr.length() == 0 ? null : arr.optJSONObject(arr.length() - 1);
    }

    private long lastTimestamp(String peer) {
        JSONObject m = lastMessage(peer);
        return m == null ? 0 : m.optLong("ts", 0);
    }

    private boolean isPeerConnected(String peer) {
        String ep = peerToEndpoint.get(normalizePeer(peer));
        return ep != null && connected.contains(ep);
    }

    private String shortTime(long ts) {
        long diff = System.currentTimeMillis() - ts;
        if (diff < 24L * 60 * 60 * 1000) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(ts));
    }

    private String initials(String peer) {
        String s = stripAt(peer).trim();
        if (s.isEmpty()) return "L";
        return s.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String sanitizeUser(String raw) {
        String x = stripAt(raw).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (x.length() > 24) x = x.substring(0, 24);
        return x;
    }

    private String normalizePeer(String peer) {
        return "@" + sanitizeUser(peer);
    }

    private String stripAt(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.startsWith("@")) s = s.substring(1);
        return s;
    }

    private String key(String s) {
        return stripAt(s).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (Exception e) { return ""; }
    }

    private String[] requiredNearbyPermissions() {
        List<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (Build.VERSION.SDK_INT <= 31) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        return p.toArray(new String[0]);
    }

    private boolean hasNearbyPermissions() {
        for (String permission : requiredNearbyPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NEARBY) return;
        if (hasNearbyPermissions()) {
            startAdvertising();
            if (pendingDiscoverAfterPermission) {
                pendingDiscoverAfterPermission = false;
                startDiscovery();
            }
        } else {
            Toast.makeText(MainActivity.this, "O LIV precisa da permissão Aparelhos próximos para conversar offline.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (inChat) showHome(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nearby != null) {
            nearby.stopDiscovery();
            nearby.stopAdvertising();
            nearby.stopAllEndpoints();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
