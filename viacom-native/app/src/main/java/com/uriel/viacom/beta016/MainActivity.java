package com.uriel.livmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
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

    private static final String SERVICE_ID = "com.uriel.livmessenger.offline.v3";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final int REQ_NEARBY = 901;
    private static final int TAB_CHATS = 0;
    private static final int TAB_NEARBY = 1;
    private static final int TAB_STATUS = 2;
    private static final int TAB_CALLS = 3;

    private final int BG = Color.rgb(8, 18, 25);
    private final int BAR = Color.rgb(13, 30, 40);
    private final int SURFACE = Color.rgb(18, 36, 47);
    private final int GREEN = Color.rgb(31, 211, 175);
    private final int TEXT = Color.rgb(241, 247, 249);
    private final int MUTED = Color.rgb(143, 160, 171);
    private final int LINE = Color.rgb(35, 58, 70);
    private final int YELLOW = Color.rgb(244, 194, 84);
    private final int BUBBLE_ME = Color.rgb(8, 105, 86);
    private final int BUBBLE_OTHER = Color.rgb(27, 45, 56);

    private ConnectionsClient nearby;
    private android.content.SharedPreferences prefs;
    private String username = "";
    private int currentTab = TAB_CHATS;
    private boolean inChat = false;
    private String activePeer = null;
    private String nearbyState = "Toque em atualizar para procurar pessoas próximas.";
    private boolean pendingDiscoverAfterPermission = false;

    private final Map<String, String> discovered = new LinkedHashMap<>();
    private final Map<String, String> endpointToPeer = new HashMap<>();
    private final Map<String, String> peerToEndpoint = new HashMap<>();
    private final Map<String, String> pendingNames = new HashMap<>();
    private final Set<String> connected = new HashSet<>();
    private final Map<String, SecretKey> sessionKeys = new HashMap<>();

    private KeyPair localKeyPair;
    private final SecureRandom random = new SecureRandom();

    private LinearLayout contentHost;
    private ScrollView chatScroll;
    private LinearLayout messageHost;
    private EditText messageInput;
    private TextView chatStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 14, 20));
        getWindow().setNavigationBarColor(Color.rgb(5, 14, 20));
        prefs = getSharedPreferences("liv_messenger_v3", MODE_PRIVATE);
        username = prefs.getString("username", "").trim();
        nearby = Nearby.getConnectionsClient(this);
        ensureKeyPair();
        showHome();
        if (username.isEmpty()) showProfileDialog(true);
        else if (hasNearbyPermissions() && isBluetoothOn()) startAdvertising(false);
    }

    private void showHome() {
        inChat = false;
        activePeer = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildHeader());
        root.addView(buildTabs());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(12), dp(10), dp(12), dp(96));
        scroll.addView(contentHost, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout fabBar = new LinearLayout(this);
        fabBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        fabBar.setPadding(dp(14), dp(4), dp(14), dp(12));
        fabBar.setBackgroundColor(BG);
        Button fab = circleButton(currentTab == TAB_NEARBY ? "⌁" : currentTab == TAB_CHATS ? "✎" : "+", 25, GREEN, Color.rgb(2, 33, 31));
        fab.setOnClickListener(v -> {
            if (currentTab == TAB_CHATS) {
                currentTab = TAB_NEARBY;
                showHome();
                ensureReadyAndDiscover();
            } else if (currentTab == TAB_NEARBY) ensureReadyAndDiscover();
            else if (currentTab == TAB_STATUS) showFeature("Novo status", "Foto, texto e vídeo offline entram na próxima etapa do LIV.");
            else showFeature("Nova chamada", "Chamadas de voz e vídeo entram depois da base de mensagens ficar estável.");
        });
        fabBar.addView(fab, new LinearLayout.LayoutParams(dp(62), dp(62)));
        root.addView(fabBar);
        setContentView(root);
        renderCurrentTab();
    }

    private View buildHeader() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(8), dp(10));
        top.setBackgroundColor(BAR);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("LIV", 30, true, GREEN));
        TextView sub = text(username.isEmpty() ? "Configure seu @usuário" : "@" + username, 13, false, MUTED);
        sub.setPadding(0, dp(1), 0, 0);
        titleBox.addView(sub);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button search = iconButton("⌕", 27);
        search.setOnClickListener(v -> searchChats());
        top.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button menu = iconButton("⋮", 29);
        menu.setOnClickListener(v -> showMainMenu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return top;
    }

    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(BAR);
        tabs.setPadding(dp(3), 0, dp(3), 0);
        tabs.addView(tab("◉", "CONVERSAS", TAB_CHATS), new LinearLayout.LayoutParams(0, dp(68), 1));
        tabs.addView(tab("⌁", "PRÓXIMOS", TAB_NEARBY), new LinearLayout.LayoutParams(0, dp(68), 1));
        tabs.addView(tab("○", "STATUS", TAB_STATUS), new LinearLayout.LayoutParams(0, dp(68), 1));
        tabs.addView(tab("☎", "CHAMADAS", TAB_CALLS), new LinearLayout.LayoutParams(0, dp(68), 1));
        return tabs;
    }

    private View tab(String icon, String label, int tabId) {
        boolean active = currentTab == tabId;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        box.setPadding(dp(2), dp(5), dp(2), 0);
        box.setClickable(true);
        box.setOnClickListener(v -> {
            currentTab = tabId;
            showHome();
            if (tabId == TAB_NEARBY) ensureReadyAndDiscover();
        });
        TextView ic = text(icon, 20, false, active ? GREEN : MUTED);
        ic.setGravity(Gravity.CENTER);
        box.addView(ic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)));
        TextView lb = text(label, 11, active, active ? GREEN : MUTED);
        lb.setGravity(Gravity.CENTER);
        box.addView(lb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
        View line = new View(this);
        line.setBackgroundColor(active ? GREEN : Color.TRANSPARENT);
        box.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        return box;
    }

    private void renderCurrentTab() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        if (currentTab == TAB_CHATS) renderChats(null);
        else if (currentTab == TAB_NEARBY) renderNearby();
        else if (currentTab == TAB_STATUS) renderStatus();
        else renderCalls();
    }

    private void renderChats(String filter) {
        contentHost.removeAllViews();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("Conversas", 22, true, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(badge("  CRIPTOGRAFADO  ", GREEN, Color.rgb(9, 61, 54)));
        contentHost.addView(titleRow, marginBottom(10));

        List<String> peers = new ArrayList<>(prefs.getStringSet("peers", Collections.emptySet()));
        peers.sort((a, b) -> Long.compare(lastTimestamp(b), lastTimestamp(a)));
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        if (peers.isEmpty()) {
            LinearLayout welcome = card();
            welcome.setPadding(dp(18), dp(18), dp(18), dp(18));
            welcome.addView(text("◉", 34, false, GREEN));
            TextView h = text("Suas conversas começam aqui", 20, true, TEXT);
            h.setPadding(0, dp(10), 0, 0);
            welcome.addView(h);
            TextView body = text("Encontre outro usuário LIV por perto e converse sem depender da internet.", 14, false, MUTED);
            body.setPadding(0, dp(7), 0, dp(14));
            welcome.addView(body);
            Button go = primaryButton("ENCONTRAR PESSOAS PRÓXIMAS");
            go.setOnClickListener(v -> { currentTab = TAB_NEARBY; showHome(); ensureReadyAndDiscover(); });
            welcome.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            contentHost.addView(welcome);
            return;
        }

        int shown = 0;
        for (String peer : peers) {
            if (!f.isEmpty() && !peer.toLowerCase(Locale.ROOT).contains(f)) continue;
            JSONObject last = lastMessage(peer);
            String preview = last == null ? "Conversa iniciada" : last.optString("text", "Mensagem");
            long ts = last == null ? 0 : last.optLong("ts", 0);
            String state = last == null ? "" : last.optString("state", "");
            boolean online = isPeerConnected(peer);

            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setClickable(true);
            row.setOnClickListener(v -> openChat(peer));
            row.addView(avatar(peer, online), new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(dp(12), 0, dp(6), 0);
            middle.addView(text("@" + stripAt(peer), 17, true, TEXT));
            TextView pv = text(preview, 14, false, online ? Color.rgb(188, 214, 212) : MUTED);
            pv.setMaxLines(1);
            pv.setPadding(0, dp(4), 0, 0);
            middle.addView(pv);
            row.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(text(ts == 0 ? "" : shortTime(ts), 11, false, online ? GREEN : MUTED));
            TextView st = badge(online ? " PRÓXIMO " : "queued".equals(state) ? " NA FILA " : " OFFLINE ", online ? GREEN : "queued".equals(state) ? YELLOW : MUTED, online ? Color.rgb(7, 62, 52) : Color.rgb(28, 44, 54));
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stLp.topMargin = dp(8);
            right.addView(st, stLp);
            row.addView(right);
            contentHost.addView(row, marginBottom(8));
            shown++;
        }
        if (shown == 0) addEmptyCard("Nenhuma conversa encontrada", "Tente pesquisar outro @usuário.");
    }

    private void renderNearby() {
        contentHost.removeAllViews();
        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView radar = text("⌁", 28, false, GREEN);
        radar.setGravity(Gravity.CENTER);
        radar.setBackground(roundRect(Color.rgb(7, 67, 58), Color.TRANSPARENT, 24, 0));
        heroRow.addView(radar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setPadding(dp(12), 0, 0, 0);
        heroText.addView(text("Conecte-se sem internet", 18, true, TEXT));
        heroText.addView(text("O LIV usa os rádios do celular para encontrar outros LIV por perto.", 13, false, MUTED));
        heroRow.addView(heroText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hero.addView(heroRow);
        TextView state = text(nearbyState, 12, false, MUTED);
        state.setPadding(0, dp(10), 0, 0);
        hero.addView(state);
        contentHost.addView(hero, marginBottom(14));

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.addView(text("Pessoas por perto", 20, true, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = ghostButton("ATUALIZAR  ↻");
        refresh.setOnClickListener(v -> ensureReadyAndDiscover());
        section.addView(refresh, new LinearLayout.LayoutParams(dp(128), dp(42)));
        contentHost.addView(section, marginBottom(10));

        if (discovered.isEmpty()) {
            LinearLayout scanning = card();
            scanning.setGravity(Gravity.CENTER_HORIZONTAL);
            scanning.setPadding(dp(18), dp(26), dp(18), dp(26));
            TextView r = text("⌁", 40, false, GREEN);
            r.setGravity(Gravity.CENTER);
            scanning.addView(r);
            TextView h = text("Procurando usuários LIV…", 17, true, TEXT);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, dp(10), 0, 0);
            scanning.addView(h);
            TextView b = text("Mantenha Bluetooth ligado nos dois aparelhos. O Wi‑Fi pode ficar sem internet.", 13, false, MUTED);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(8), dp(7), dp(8), 0);
            scanning.addView(b);
            contentHost.addView(scanning);
            return;
        }

        for (Map.Entry<String, String> e : discovered.entrySet()) {
            String endpoint = e.getKey();
            String peer = normalizePeer(e.getValue());
            boolean isConnected = connected.contains(endpoint);
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(10), dp(11));
            row.addView(avatar(peer, true), new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(12), 0, dp(8), 0);
            mid.addView(text("@" + stripAt(peer), 16, true, TEXT));
            TextView status = text(isConnected ? "● Conectado • sessão protegida" : "● Perto • offline local", 12, false, isConnected ? GREEN : Color.rgb(123, 198, 182));
            status.setPadding(0, dp(4), 0, 0);
            mid.addView(status);
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button action = outlineButton(isConnected ? "ABRIR" : "CONECTAR");
            action.setOnClickListener(v -> { if (isConnected) openChat(peer); else connectTo(endpoint, peer); });
            row.addView(action, new LinearLayout.LayoutParams(dp(112), dp(44)));
            contentHost.addView(row, marginBottom(8));
        }
    }

    private void renderStatus() {
        contentHost.removeAllViews();
        contentHost.addView(text("Status", 22, true, TEXT), marginBottom(10));
        LinearLayout mine = card();
        mine.setOrientation(LinearLayout.HORIZONTAL);
        mine.setGravity(Gravity.CENTER_VERTICAL);
        mine.setPadding(dp(12), dp(12), dp(12), dp(12));
        mine.addView(avatar("@" + username, true), new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(12), 0, 0, 0);
        t.addView(text("Meu status", 17, true, TEXT));
        t.addView(text("Toque para adicionar uma atualização", 13, false, MUTED));
        mine.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView plus = text("+", 28, true, GREEN);
        plus.setGravity(Gravity.CENTER);
        mine.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        mine.setOnClickListener(v -> showFeature("Status LIV", "Status local por foto, vídeo e texto será ativado numa próxima build."));
        contentHost.addView(mine, marginBottom(18));
        contentHost.addView(text("ATUALIZAÇÕES RECENTES", 12, true, MUTED), marginBottom(8));
        addEmptyCard("Nenhum status recente", "Quando usuários LIV próximos publicarem um status, ele aparecerá aqui.");
    }

    private void renderCalls() {
        contentHost.removeAllViews();
        contentHost.addView(text("Chamadas", 22, true, TEXT), marginBottom(10));
        LinearLayout link = card();
        link.setOrientation(LinearLayout.HORIZONTAL);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView icon = text("↗", 25, true, GREEN);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(Color.rgb(7, 67, 58), Color.TRANSPARENT, 24, 0));
        link.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(text("Criar chamada LIV", 17, true, TEXT));
        tx.addView(text("Voz e vídeo local entre usuários próximos", 13, false, MUTED));
        link.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        link.setOnClickListener(v -> showFeature("Chamadas LIV", "A interface já está preparada. O motor de voz e vídeo entra depois do chat offline."));
        contentHost.addView(link, marginBottom(18));
        contentHost.addView(text("RECENTES", 12, true, MUTED), marginBottom(8));
        addEmptyCard("Nenhuma chamada recente", "Suas chamadas LIV aparecerão aqui.");
    }

    private void openChat(String peer) {
        inChat = true;
        activePeer = normalizePeer(peer);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(6, 16, 22));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(5), dp(8), dp(8), dp(8));
        top.setBackgroundColor(BAR);
        Button back = iconButton("‹", 33);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        top.addView(avatar(activePeer, isPeerConnected(activePeer)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(dp(10), 0, 0, 0);
        who.addView(text("@" + stripAt(activePeer), 17, true, TEXT));
        chatStatus = text(isPeerConnected(activePeer) ? "● Perto • offline local • protegido" : "offline • mensagens ficam na fila", 11, false, isPeerConnected(activePeer) ? GREEN : MUTED);
        who.addView(chatStatus);
        top.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button call = iconButton("☎", 22);
        call.setOnClickListener(v -> showFeature("Chamada LIV", "Chamadas entram numa próxima build."));
        top.addView(call, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button menu = iconButton("⋮", 27);
        menu.setOnClickListener(v -> showChatMenu(activePeer));
        top.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messageHost = new LinearLayout(this);
        messageHost.setOrientation(LinearLayout.VERTICAL);
        messageHost.setPadding(dp(10), dp(14), dp(10), dp(12));
        chatScroll.addView(messageHost, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout compose = new LinearLayout(this);
        compose.setGravity(Gravity.CENTER_VERTICAL);
        compose.setPadding(dp(7), dp(6), dp(7), dp(9));
        compose.setBackgroundColor(Color.rgb(6, 16, 22));
        LinearLayout inputShell = new LinearLayout(this);
        inputShell.setGravity(Gravity.CENTER_VERTICAL);
        inputShell.setPadding(dp(3), 0, dp(3), 0);
        inputShell.setBackground(roundRect(SURFACE, LINE, 25, 1));
        Button smile = iconButton("☺", 21);
        smile.setOnClickListener(v -> insertText("🙂"));
        inputShell.addView(smile, new LinearLayout.LayoutParams(dp(44), dp(50)));
        messageInput = new EditText(this);
        messageInput.setHint("Mensagem");
        messageInput.setHintTextColor(MUTED);
        messageInput.setTextColor(TEXT);
        messageInput.setTextSize(16);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        messageInput.setPadding(dp(4), 0, dp(4), 0);
        messageInput.setBackgroundColor(Color.TRANSPARENT);
        inputShell.addView(messageInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button attach = iconButton("＋", 25);
        attach.setOnClickListener(v -> showAttachmentMenu());
        inputShell.addView(attach, new LinearLayout.LayoutParams(dp(44), dp(50)));
        compose.addView(inputShell, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button send = circleButton("➤", 22, GREEN, Color.rgb(2, 33, 31));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        sendLp.leftMargin = dp(7);
        compose.addView(send, sendLp);
        send.setOnClickListener(v -> sendCurrentMessage());
        root.addView(compose);
        setContentView(root);
        renderMessages();
        if (isPeerConnected(activePeer)) sendReadMarker();
    }

    private void renderMessages() {
        if (messageHost == null || activePeer == null) return;
        messageHost.removeAllViews();
        TextView date = badge("  HOJE  ", MUTED, Color.rgb(18, 35, 44));
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER);
        dateRow.addView(date);
        messageHost.addView(dateRow, marginBottom(10));
        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER);
        TextView notice = text("  🔒 Conversa LIV protegida. Funciona localmente sem internet.  ", 11, false, Color.rgb(146, 205, 193));
        notice.setGravity(Gravity.CENTER);
        notice.setBackground(roundRect(Color.rgb(10, 45, 48), Color.TRANSPARENT, 13, 0));
        info.addView(notice);
        messageHost.addView(info, marginBottom(12));
        JSONArray arr = loadMessages(activePeer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m != null) addBubble(m.optString("text", ""), m.optBoolean("mine", false), m.optLong("ts", System.currentTimeMillis()), m.optString("state", ""));
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addBubble(String body, boolean mine, long ts, String state) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(mine ? dp(55) : 0, dp(3), mine ? 0 : dp(55), dp(3));
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(10), dp(6));
        bubble.setBackground(roundRect(mine ? BUBBLE_ME : BUBBLE_OTHER, Color.TRANSPARENT, 16, 0));
        bubble.addView(text(body, 16, false, TEXT));
        String marks = "";
        if (mine) {
            if ("delivered".equals(state) || "read".equals(state)) marks = "  ✓✓";
            else if ("queued".equals(state)) marks = "  ◷";
            else marks = "  ✓";
        }
        TextView meta = text(shortTime(ts) + marks, 10, false, mine && ("delivered".equals(state) || "read".equals(state)) ? Color.rgb(88, 220, 220) : MUTED);
        meta.setGravity(Gravity.END);
        meta.setPadding(dp(20), dp(3), 0, 0);
        bubble.addView(meta);
        row.addView(bubble, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        messageHost.addView(row);
    }

    private void sendCurrentMessage() {
        if (messageInput == null || activePeer == null) return;
        String body = messageInput.getText().toString().trim();
        if (body.isEmpty()) return;
        messageInput.setText("");
        hideKeyboard();
        String id = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();
        String endpoint = peerToEndpoint.get(activePeer);
        SecretKey key = endpoint == null ? null : sessionKeys.get(endpoint);
        boolean ready = endpoint != null && connected.contains(endpoint) && key != null;
        saveMessage(activePeer, id, body, true, ts, ready ? "sent" : "queued");
        renderMessages();
        if (ready) sendEncrypted(endpoint, activePeer, id, body, ts, key);
        else if (endpoint != null && connected.contains(endpoint)) sendPublicKey(endpoint);
    }

    private void sendEncrypted(String endpoint, String peer, String id, String body, long ts, SecretKey key) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String packet = "MSG|" + id + "|" + ts + "|" + Base64.encodeToString(iv, Base64.NO_WRAP) + "|" + Base64.encodeToString(ct, Base64.NO_WRAP);
            nearby.sendPayload(endpoint, Payload.fromBytes(packet.getBytes(StandardCharsets.UTF_8)))
                    .addOnSuccessListener(unused -> updateMessageState(peer, id, "sent"))
                    .addOnFailureListener(e -> {
                        updateMessageState(peer, id, "queued");
                        runOnUiThread(() -> { if (inChat && peer.equals(activePeer)) renderMessages(); Toast.makeText(MainActivity.this, "Mensagem ficou na fila para tentar novamente.", Toast.LENGTH_SHORT).show(); });
                    });
        } catch (Exception e) { updateMessageState(peer, id, "queued"); }
    }

    private void flushQueued(String endpoint, String peer) {
        SecretKey key = sessionKeys.get(endpoint);
        if (key == null) return;
        JSONArray arr = loadMessages(peer);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m != null && m.optBoolean("mine", false) && "queued".equals(m.optString("state"))) sendEncrypted(endpoint, peer, m.optString("id"), m.optString("text"), m.optLong("ts"), key);
        }
    }

    private void ensureReadyAndDiscover() {
        if (username.isEmpty()) { showProfileDialog(true); return; }
        if (!hasNearbyPermissions()) { pendingDiscoverAfterPermission = true; requestNearbyPermissions(); return; }
        if (!isBluetoothOn()) {
            new AlertDialog.Builder(this)
                    .setTitle("Ative o Bluetooth")
                    .setMessage("O LIV precisa do Bluetooth ligado para localizar outros aparelhos próximos. O Wi‑Fi pode ficar sem internet.")
                    .setPositiveButton("ABRIR BLUETOOTH", (d, w) -> { try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Exception ignored) {} })
                    .setNegativeButton("AGORA NÃO", null).show();
            nearbyState = "Bluetooth desligado. Ative-o para procurar usuários.";
            renderCurrentTab();
            return;
        }
        startAdvertising(true);
        startDiscovery();
    }

    private boolean isBluetoothOn() {
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException e) { return false; }
    }

    private void startAdvertising(boolean announce) {
        nearby.stopAdvertising();
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        nearby.startAdvertising("@" + username, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> runOnUiThread(() -> { nearbyState = "LIV visível. Procurando aparelhos próximos…"; if (announce && currentTab == TAB_NEARBY) renderNearby(); }))
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyFailure("Não foi possível deixar o LIV visível", e)));
    }

    private void startDiscovery() {
        discovered.clear();
        if (currentTab == TAB_NEARBY) renderNearby();
        nearby.stopDiscovery();
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        nearby.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> runOnUiThread(() -> { nearbyState = "Busca ativa. Deixe o LIV aberto no outro aparelho."; if (currentTab == TAB_NEARBY) renderNearby(); }))
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyFailure("Falha ao procurar usuários próximos", e)));
    }

    private void nearbyFailure(String title, Exception e) {
        int code = e instanceof ApiException ? ((ApiException) e).getStatusCode() : -1;
        nearbyState = title + (code >= 0 ? " • código " + code : "") + ". Verifique Bluetooth e permissões.";
        if (currentTab == TAB_NEARBY) renderNearby();
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Verifique se o Bluetooth está ligado e se o LIV tem permissão para Aparelhos próximos." + (code >= 0 ? "\n\nCódigo técnico: " + code : ""))
                .setPositiveButton("PERMISSÕES", (d, w) -> { try { Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.setData(android.net.Uri.parse("package:" + getPackageName())); startActivity(intent); } catch (Exception ignored) {} })
                .setNegativeButton("FECHAR", null).show();
    }

    private void connectTo(String endpoint, String peer) {
        pendingNames.put(endpoint, normalizePeer(peer));
        nearby.requestConnection("@" + username, endpoint, connectionLifecycleCallback)
                .addOnFailureListener(e -> runOnUiThread(() -> nearbyFailure("Não foi possível iniciar a conexão", e)));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            if (peer.equals(normalizePeer(username))) return;
            discovered.put(endpointId, peer);
            endpointToPeer.put(endpointId, peer);
            runOnUiThread(() -> { if (currentTab == TAB_NEARBY && !inChat) renderNearby(); });
        }
        @Override public void onEndpointLost(String endpointId) {
            discovered.remove(endpointId);
            runOnUiThread(() -> { if (currentTab == TAB_NEARBY && !inChat) renderNearby(); });
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
            String peer = normalizePeer(info.getEndpointName());
            pendingNames.put(endpointId, peer);
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Conectar com @" + stripAt(peer) + "?")
                    .setMessage("Compare este código nos dois aparelhos:\n\n" + info.getAuthenticationDigits())
                    .setPositiveButton("CONECTAR", (d, w) -> nearby.acceptConnection(endpointId, payloadCallback))
                    .setNegativeButton("RECUSAR", (d, w) -> nearby.rejectConnection(endpointId))
                    .setCancelable(false).show());
        }
        @Override public void onConnectionResult(String endpointId, ConnectionResolution result) {
            runOnUiThread(() -> {
                if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                    String peer = normalizePeer(pendingNames.getOrDefault(endpointId, endpointToPeer.getOrDefault(endpointId, "@usuario")));
                    connected.add(endpointId);
                    endpointToPeer.put(endpointId, peer);
                    peerToEndpoint.put(peer, endpointId);
                    addPeer(peer);
                    sendPublicKey(endpointId);
                    nearbyState = "Conectado a @" + stripAt(peer) + ".";
                    Toast.makeText(MainActivity.this, "Conectado a @" + stripAt(peer), Toast.LENGTH_SHORT).show();
                    if (currentTab == TAB_NEARBY && !inChat) renderNearby();
                } else {
                    nearbyState = "Conexão não concluída • código " + result.getStatus().getStatusCode();
                    if (currentTab == TAB_NEARBY && !inChat) renderNearby();
                }
            });
        }
        @Override public void onDisconnected(String endpointId) {
            String peer = endpointToPeer.get(endpointId);
            connected.remove(endpointId);
            sessionKeys.remove(endpointId);
            if (peer != null) peerToEndpoint.remove(peer);
            runOnUiThread(() -> {
                if (inChat && peer != null && peer.equals(activePeer) && chatStatus != null) { chatStatus.setText("offline • mensagens ficam na fila"); chatStatus.setTextColor(MUTED); }
                else if (!inChat) renderCurrentTab();
            });
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            String packet = new String(payload.asBytes(), StandardCharsets.UTF_8);
            if (packet.startsWith("KEY|")) handlePublicKey(endpointId, packet.substring(4));
            else if (packet.startsWith("MSG|")) handleEncryptedMessage(endpointId, packet);
            else if (packet.startsWith("ACK|")) handleAck(endpointId, packet.substring(4));
            else if (packet.startsWith("READ|")) handleRead(endpointId, packet.substring(5));
        }
        @Override public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {}
    };

    private void sendPublicKey(String endpoint) {
        try { String b64 = Base64.encodeToString(localKeyPair.getPublic().getEncoded(), Base64.NO_WRAP); nearby.sendPayload(endpoint, Payload.fromBytes(("KEY|" + b64).getBytes(StandardCharsets.UTF_8))); } catch (Exception ignored) {}
    }

    private void handlePublicKey(String endpoint, String b64) {
        try {
            PublicKey remote = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)));
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(localKeyPair.getPrivate());
            ka.doPhase(remote, true);
            SecretKey key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(ka.generateSecret()), "AES");
            sessionKeys.put(endpoint, key);
            String peer = endpointToPeer.get(endpoint);
            if (peer != null) flushQueued(endpoint, peer);
            runOnUiThread(() -> { if (inChat && peer != null && peer.equals(activePeer) && chatStatus != null) { chatStatus.setText("● Perto • offline local • protegido"); chatStatus.setTextColor(GREEN); } });
        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível preparar a sessão protegida.", Toast.LENGTH_SHORT).show()); }
    }

    private void handleEncryptedMessage(String endpoint, String packet) {
        try {
            String[] p = packet.split("\\|", 5);
            if (p.length != 5) return;
            SecretKey key = sessionKeys.get(endpoint);
            if (key == null) { sendPublicKey(endpoint); return; }
            String id = p[1];
            long ts = Long.parseLong(p[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(p[3], Base64.DEFAULT)));
            String body = new String(cipher.doFinal(Base64.decode(p[4], Base64.DEFAULT)), StandardCharsets.UTF_8);
            String peer = normalizePeer(endpointToPeer.getOrDefault(endpoint, "@usuario"));
            addPeer(peer);
            saveMessage(peer, id, body, false, ts, "received");
            nearby.sendPayload(endpoint, Payload.fromBytes(("ACK|" + id).getBytes(StandardCharsets.UTF_8)));
            runOnUiThread(() -> {
                if (inChat && peer.equals(activePeer)) { renderMessages(); sendReadMarker(); }
                else { Toast.makeText(MainActivity.this, "Nova mensagem de @" + stripAt(peer), Toast.LENGTH_SHORT).show(); if (currentTab == TAB_CHATS) renderChats(null); }
            });
        } catch (Exception ignored) {}
    }

    private void handleAck(String endpoint, String id) {
        String peer = endpointToPeer.get(endpoint);
        if (peer == null) return;
        updateMessageState(peer, id, "delivered");
        runOnUiThread(() -> { if (inChat && peer.equals(activePeer)) renderMessages(); });
    }

    private void sendReadMarker() {
        if (activePeer == null) return;
        String endpoint = peerToEndpoint.get(activePeer);
        if (endpoint == null || !connected.contains(endpoint)) return;
        JSONArray arr = loadMessages(activePeer);
        String lastIncoming = null;
        for (int i = arr.length() - 1; i >= 0; i--) { JSONObject m = arr.optJSONObject(i); if (m != null && !m.optBoolean("mine", false)) { lastIncoming = m.optString("id", null); break; } }
        if (lastIncoming != null) nearby.sendPayload(endpoint, Payload.fromBytes(("READ|" + lastIncoming).getBytes(StandardCharsets.UTF_8)));
    }

    private void handleRead(String endpoint, String id) {
        String peer = endpointToPeer.get(endpoint);
        if (peer == null) return;
        markUpToRead(peer, id);
        runOnUiThread(() -> { if (inChat && peer.equals(activePeer)) renderMessages(); });
    }

    private void showProfileDialog(boolean required) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText user = new EditText(this);
        user.setHint("@usuário");
        user.setText(username);
        user.setSingleLine(true);
        EditText pass = new EditText(this);
        pass.setHint("Senha local");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(user); box.addView(pass);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(required ? "Crie seu perfil LIV" : "Perfil")
                .setMessage("No LIV você usa um @usuário, sem número de telefone.")
                .setView(box).setPositiveButton("SALVAR", null)
                .setNegativeButton(required ? "" : "CANCELAR", null).setCancelable(!required).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = user.getText().toString().trim().replace("@", "").replace(" ", "").toLowerCase(Locale.ROOT);
            if (u.length() < 3) { user.setError("Use pelo menos 3 caracteres"); return; }
            username = u;
            android.content.SharedPreferences.Editor ed = prefs.edit().putString("username", username);
            if (!pass.getText().toString().isEmpty()) ed.putString("password_hash", sha256(pass.getText().toString()));
            ed.apply();
            dialog.dismiss();
            showHome();
            if (hasNearbyPermissions() && isBluetoothOn()) startAdvertising(false);
        }));
        dialog.show();
    }

    private void showMainMenu() {
        String[] items = {"Perfil", "Privacidade e segurança", "Backup", "Aparelhos próximos", "Sobre o LIV"};
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showProfileDialog(false);
            else if (which == 1) showFeature("Privacidade e segurança", "As mensagens diretas usam sessão criptografada entre os aparelhos. A próxima etapa inclui identidade persistente e chaves de recuperação.");
            else if (which == 2) showFeature("Backup", "O backup criptografado no Google Drive será feito apenas quando houver Wi‑Fi e estiver habilitado nas configurações.");
            else if (which == 3) { currentTab = TAB_NEARBY; showHome(); ensureReadyAndDiscover(); }
            else showFeature("LIV Messenger", "Versão 0.4.1\nMensageiro offline-first por @usuário.\nSem QR Code e sem número de telefone.");
        }).show();
    }

    private void showChatMenu(String peer) {
        String[] items = {"Ver perfil", "Pesquisar na conversa", "Limpar conversa"};
        new AlertDialog.Builder(this).setTitle("@" + stripAt(peer)).setItems(items, (d, which) -> {
            if (which == 0) showFeature("@" + stripAt(peer), isPeerConnected(peer) ? "Usuário próximo • conexão local protegida" : "Usuário offline");
            else if (which == 1) searchInConversation(peer);
            else new AlertDialog.Builder(this).setTitle("Limpar conversa?").setMessage("As mensagens deste aparelho serão apagadas.")
                    .setPositiveButton("LIMPAR", (x, y) -> { prefs.edit().remove(messagesKey(peer)).apply(); renderMessages(); })
                    .setNegativeButton("CANCELAR", null).show();
        }).show();
    }

    private void showAttachmentMenu() {
        String[] items = {"📷  Câmera", "🖼  Galeria", "📄  Documento", "📍  Localização", "🎤  Áudio"};
        new AlertDialog.Builder(this).setTitle("Enviar").setItems(items, (d, which) -> showFeature("Anexo LIV", "A interface está pronta. O envio deste tipo de anexo entra na próxima atualização.")).show();
    }

    private void searchChats() {
        if (currentTab != TAB_CHATS) { currentTab = TAB_CHATS; showHome(); }
        EditText input = new EditText(this);
        input.setHint("Pesquisar @usuário");
        new AlertDialog.Builder(this).setTitle("Pesquisar conversas").setView(input)
                .setPositiveButton("PESQUISAR", (d, w) -> renderChats(input.getText().toString()))
                .setNegativeButton("CANCELAR", null).show();
    }

    private void searchInConversation(String peer) {
        EditText input = new EditText(this);
        input.setHint("Texto da mensagem");
        new AlertDialog.Builder(this).setTitle("Pesquisar na conversa").setView(input)
                .setPositiveButton("PESQUISAR", (d, w) -> {
                    String q = input.getText().toString().toLowerCase(Locale.ROOT).trim();
                    JSONArray arr = loadMessages(peer);
                    int count = 0;
                    for (int i = 0; i < arr.length(); i++) { JSONObject m = arr.optJSONObject(i); if (m != null && m.optString("text").toLowerCase(Locale.ROOT).contains(q)) count++; }
                    Toast.makeText(this, count + " mensagem(ns) encontrada(s)", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("CANCELAR", null).show();
    }

    private void showFeature(String title, String body) { new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show(); }

    private boolean hasNearbyPermissions() {
        if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNearbyPermissions() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_NEARBY);
        else if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}, REQ_NEARBY);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_NEARBY);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NEARBY) return;
        if (hasNearbyPermissions()) {
            startAdvertising(false);
            if (pendingDiscoverAfterPermission) { pendingDiscoverAfterPermission = false; ensureReadyAndDiscover(); }
        } else {
            nearbyState = "Permissão Aparelhos próximos não concedida.";
            Toast.makeText(this, "O LIV precisa da permissão Aparelhos próximos para conversar offline.", Toast.LENGTH_LONG).show();
            if (currentTab == TAB_NEARBY) renderNearby();
        }
    }

    private void ensureKeyPair() {
        try { KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC"); kpg.initialize(new ECGenParameterSpec("secp256r1")); localKeyPair = kpg.generateKeyPair(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private void addPeer(String peer) {
        Set<String> peers = new HashSet<>(prefs.getStringSet("peers", Collections.emptySet()));
        peers.add(normalizePeer(peer));
        prefs.edit().putStringSet("peers", peers).apply();
    }

    private JSONArray loadMessages(String peer) { try { return new JSONArray(prefs.getString(messagesKey(peer), "[]")); } catch (Exception e) { return new JSONArray(); } }

    private void saveMessage(String peer, String id, String body, boolean mine, long ts, String state) {
        try {
            JSONArray arr = loadMessages(peer);
            for (int i = 0; i < arr.length(); i++) { JSONObject existing = arr.optJSONObject(i); if (existing != null && id.equals(existing.optString("id"))) return; }
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("text", body); o.put("mine", mine); o.put("ts", ts); o.put("state", state);
            arr.put(o);
            prefs.edit().putString(messagesKey(peer), arr.toString()).apply();
            addPeer(peer);
        } catch (Exception ignored) {}
    }

    private void updateMessageState(String peer, String id, String state) {
        try {
            JSONArray arr = loadMessages(peer);
            for (int i = 0; i < arr.length(); i++) { JSONObject m = arr.optJSONObject(i); if (m != null && id.equals(m.optString("id"))) m.put("state", state); }
            prefs.edit().putString(messagesKey(peer), arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void markUpToRead(String peer, String id) {
        try {
            JSONArray arr = loadMessages(peer);
            for (int i = 0; i < arr.length(); i++) { JSONObject m = arr.optJSONObject(i); if (m != null && m.optBoolean("mine", false)) m.put("state", "read"); if (m != null && id.equals(m.optString("id"))) break; }
            prefs.edit().putString(messagesKey(peer), arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private JSONObject lastMessage(String peer) { JSONArray arr = loadMessages(peer); return arr.length() == 0 ? null : arr.optJSONObject(arr.length() - 1); }
    private long lastTimestamp(String peer) { JSONObject m = lastMessage(peer); return m == null ? 0 : m.optLong("ts", 0); }
    private String messagesKey(String peer) { return "msgs_" + normalizePeer(peer); }
    private boolean isPeerConnected(String peer) { String endpoint = peerToEndpoint.get(normalizePeer(peer)); return endpoint != null && connected.contains(endpoint); }
    private String normalizePeer(String s) { if (s == null) return "usuario"; String x = s.trim(); while (x.startsWith("@")) x = x.substring(1); return x.toLowerCase(Locale.ROOT); }
    private String stripAt(String s) { return normalizePeer(s); }
    private String shortTime(long ts) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts)); }

    private String initials(String peer) {
        String s = stripAt(peer).replace('.', ' ').replace('_', ' ').trim();
        if (s.isEmpty()) return "L";
        String[] parts = s.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private String sha256(String s) {
        try { byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x)); return b.toString(); }
        catch (Exception e) { return ""; }
    }

    private void insertText(String s) { if (messageInput != null) { int p = Math.max(0, messageInput.getSelectionStart()); messageInput.getText().insert(p, s); messageInput.requestFocus(); } }
    private void hideKeyboard() { try { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (messageInput != null) imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0); } catch (Exception ignored) {} }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(0, 1.04f); return v;
    }

    private TextView avatar(String peer, boolean online) {
        TextView a = text(initials(peer), 16, true, online ? Color.rgb(3, 36, 33) : TEXT); a.setGravity(Gravity.CENTER); a.setBackground(roundRect(online ? GREEN : Color.rgb(62, 83, 94), Color.TRANSPARENT, 30, 0)); return a;
    }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setBackground(roundRect(SURFACE, LINE, 16, 1)); return c; }
    private TextView badge(String label, int textColor, int bg) { TextView b = text(label, 10, true, textColor); b.setGravity(Gravity.CENTER); b.setPadding(dp(5), dp(4), dp(5), dp(4)); b.setBackground(roundRect(bg, Color.TRANSPARENT, 12, 0)); return b; }

    private Button primaryButton(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(Color.rgb(3, 34, 31)); b.setAllCaps(false); b.setBackground(roundRect(GREEN, GREEN, 14, 0)); return b; }
    private Button outlineButton(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(11); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(GREEN); b.setAllCaps(false); b.setBackground(roundRect(Color.TRANSPARENT, GREEN, 12, 1)); return b; }
    private Button ghostButton(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(11); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(GREEN); b.setAllCaps(false); b.setBackgroundColor(Color.TRANSPARENT); return b; }
    private Button iconButton(String icon, int size) { Button b = new Button(this); b.setText(icon); b.setTextSize(size); b.setTextColor(TEXT); b.setAllCaps(false); b.setPadding(0, 0, 0, 0); b.setBackgroundColor(Color.TRANSPARENT); return b; }
    private Button circleButton(String icon, int size, int bg, int fg) { Button b = new Button(this); b.setText(icon); b.setTextSize(size); b.setTextColor(fg); b.setAllCaps(false); b.setPadding(0, 0, 0, 0); b.setBackground(roundRect(bg, Color.TRANSPARENT, 30, 0)); return b; }

    private GradientDrawable roundRect(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke); return d;
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(bottom); return lp; }

    private void addEmptyCard(String title, String body) {
        LinearLayout c = card(); c.setPadding(dp(18), dp(20), dp(18), dp(20)); TextView h = text(title, 18, true, TEXT); h.setGravity(Gravity.CENTER); c.addView(h); TextView b = text(body, 13, false, MUTED); b.setGravity(Gravity.CENTER); b.setPadding(0, dp(7), 0, 0); c.addView(b); contentHost.addView(c);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() { if (inChat) showHome(); else super.onBackPressed(); }
    @Override protected void onResume() { super.onResume(); if (!username.isEmpty() && hasNearbyPermissions() && isBluetoothOn()) startAdvertising(false); }
}
