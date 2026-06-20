package it.polimi.Network.Client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.Config.NetworkSettings;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.RMI.GameServiceRemote;
import it.polimi.Network.RMI.RmiLoginRequest;
import it.polimi.Network.RMI.RmiLoginResponse;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaFX-based GUI client for Mesos with Multi-Scene Navigation.
 */
public class ClientGuiMain extends Application {

    /** Creates the JavaFX client application. */
    public ClientGuiMain() {
    }

    private static final double DESIGN_WINDOW_WIDTH = 1400;
    private static final double DESIGN_WINDOW_HEIGHT = 900;
    private static final double WINDOW_ASPECT_RATIO = DESIGN_WINDOW_WIDTH / DESIGN_WINDOW_HEIGHT;
    private static final double DEFAULT_WINDOW_WIDTH = 1280;
    private static final double DEFAULT_WINDOW_HEIGHT = DEFAULT_WINDOW_WIDTH / WINDOW_ASPECT_RATIO;
    private static final double MIN_WINDOW_WIDTH = 900;
    private static final double MIN_WINDOW_HEIGHT = MIN_WINDOW_WIDTH / WINDOW_ASPECT_RATIO;

    private static final Pattern IMG_ID_PATTERN = Pattern.compile("\\{\\s*img\\s*=\\s*(\\d+)\\s*\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_METADATA_PATTERN = Pattern.compile("\\{\\s*img\\s*[:=]\\s*[^}]*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTING_ROW_PATTERN = Pattern.compile(
            "^(?:Acting|Now acting):\\s*(.+?)\\s*\\(tile\\s+[^)]*\\)\\s*remaining\\s+upper=(\\d+)\\s+lower=(\\d+).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_ROW_PATTERN = Pattern.compile(
            "^(.+?)(?:\\s+color=([A-Z]+))?\\s+food=(-?\\d+)\\s+pp=(-?\\d+)\\s+chars=\\d+\\s+buildings=\\d+\\s+tile=([^\\s]+)(?:\\s+OFFLINE)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUND_PATTERN = Pattern.compile("\\bround=(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERA_PATTERN = Pattern.compile("\\bera=([^,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYERS_COUNT_PATTERN = Pattern.compile("\\bplayers=(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private Stage window;
    private Scene connectionScene;
    private Scene mainMenuScene;
    private Scene createScene;
    private Scene joinScene;
    private Scene reconnectScene;
    private Scene gameScene;

    // Connection UI Elements
    private ComboBox<String> protocolCombo;
    private TextField hostField;
    private TextField portField;
    private TextField bindingField;
    private Button connectButton;

    // Gateway UI Elements
    private TextField createNickField;
    private TextField createRoomField;
    private TextField createPinField;
    private ComboBox<Integer> createPlayersCombo;
    
    private TextField joinNickField;
    private TextField joinRoomField;
    private TextField joinPinField;
    
    private TextField reconnectNickField;
    private TextField reconnectRoomField;
    private TextField reconnectPinField;

    // Game UI Elements
    private VBox playersBox;
    private HBox handBox;
    private HBox upperTribeBox;
    private HBox upperBuildingBox;
    private HBox lowerTribeBox;
    private HBox lowerBuildingBox;
    private HBox offerTilesBox;
    private ScrollPane offerTilesScroll;
    private VBox placedTotemsBox;
    private TextArea logArea;
    private Button readyButton;
    private Button removeReadyButton;
    private Button gatewayLobbyButton;
    private Label readyStatusLabel;
    private HBox colorSelectionBox;
    private Label colorSelectionLabel;
    private Label connectedPlayerLabel;
    private ImageView connectedPlayerTokenView;
    private Label roundLabel;
    private Label eraLabel;
    private StackPane inGameMenuOverlay;
    private HBox playersSelectionBox;
    private Label startPromptLabel;
    private VBox gameHeader;
    private VBox gameplayArea;
    private Pane gameDashboard;
    private StackPane playerHandOverlay;
    private VBox preStartPane;
    private VBox turnInfoBox;
    private Label turnOwnerLabel;
    private Label turnInstructionLabel;
    private StackPane turnOrderTrackPane;
    /** Image view showing the current player-count turn-order track. */
    private ImageView turnOrderTrackView;
    /** Transparent layer used to place token images over the turn-order track. */
    private Pane turnOrderTokenLayer;
    /** Opens the local player's complete hand when the preview row overflows. */
    private Button handExpandButton;
    private String lastLoginNickname;
    private String lastLoginPin;
    private int lastLoginRoomId;
    private int selectedPlayers = 2;
    private final List<Button> playerCountButtons = new ArrayList<>();
    private final Map<String, Button> offerTileButtons = new HashMap<>();
    private final Map<String, String> placedTotemColorsByTile = new HashMap<>();
    private final Map<String, List<Integer>> takenCardsByPlayer = new HashMap<>();
    private final List<CardDisplay> playerHandCards = new ArrayList<>();
    private String currentToPlaceNickname;

    // Action resolution gating
    private String currentActingNickname;
    private int remainingUpperPicks;
    private int remainingLowerPicks;

    // Notification Queue
    private final LinkedList<String> notificationQueue = new LinkedList<>();
    private boolean isNotificationPlaying = false;
    private boolean adjustingWindowSize = false;
    // UI sizing (adjusted on window resize)
    private double tileButtonSize = 126;
    private double tileImageFitWidth = 78;
    private double playerButtonSize = 72;
    private double playerImageFitWidth = 60;

    // Custom Styled Labels for Player Resources
    private Label foodValueLabel;
    private Label prestigeValueLabel;

    // Background Image
    private Background mainBackground;

    // Networking
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private final AtomicReference<ConnectionContext> activeConnection = new AtomicReference<>();
    private static final String TITLE_FONT = "Papyrus";
    private static final String MENU_FONT = "Herculanum";
    private static final String LABEL_FONT = "Copperplate";

    /**
     * Launches the JavaFX client application.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initializes the JavaFX stage and builds all GUI scenes.
     *
     * @param primaryStage application primary stage.
     */
    @Override
    public void start(Stage primaryStage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("Uncaught exception on thread " + thread.getName() + ": " + error);
            error.printStackTrace(System.err);
        });

        window = primaryStage;
        window.setTitle("Mesos - Ancestral Tribe");

        loadBackgroundImage();

        buildConnectionScene();
        buildMainMenuScene();
        buildActionScenes();
        buildGameScene();

        switchScene(connectionScene);
        window.setMinWidth(MIN_WINDOW_WIDTH);
        window.setMinHeight(MIN_WINDOW_HEIGHT);
        window.show();
        installProportionalWindowResize();

        adjustLayoutForHeight(DESIGN_WINDOW_HEIGHT);
    }

    /** Keeps the application content proportional when the stage is resized. */
    private void installProportionalWindowResize() {
        window.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (adjustingWindowSize || newWidth == null || window.isFullScreen()) {
                return;
            }
            adjustingWindowSize = true;
            window.setHeight(newWidth.doubleValue() / WINDOW_ASPECT_RATIO);
            adjustingWindowSize = false;
        });

        window.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (adjustingWindowSize || newHeight == null || window.isFullScreen()) {
                return;
            }
            adjustingWindowSize = true;
            window.setWidth(newHeight.doubleValue() * WINDOW_ASPECT_RATIO);
            adjustingWindowSize = false;
        });
    }

    /**
     * Releases networking resources when the JavaFX application stops.
     *
     * @throws Exception if superclass shutdown fails.
     */
    @Override
    public void stop() throws Exception {
        disconnectSilently();
        connectionExecutor.shutdownNow();
        super.stop();
    }

    /** Loads the main menu background image from resources, if available. */
    private void loadBackgroundImage() {
        Image bgImage = ImageLoader.getPreviewImage("background.png");
        if (bgImage != null) {
            BackgroundImage backgroundImage = new BackgroundImage(
                    bgImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, true)
            );
            mainBackground = new Background(backgroundImage);
        }
    }

    /**
     * Creates the scene root with either themed image background or fallback gradient.
     *
     * @return configured root stack pane.
     */
    private StackPane createRootWithBackground() {
        StackPane root = new StackPane();
        root.setMinSize(DESIGN_WINDOW_WIDTH, DESIGN_WINDOW_HEIGHT);
        root.setPrefSize(DESIGN_WINDOW_WIDTH, DESIGN_WINDOW_HEIGHT);
        root.setMaxSize(DESIGN_WINDOW_WIDTH, DESIGN_WINDOW_HEIGHT);
        root.setBackground(Background.EMPTY);
        root.setStyle("-fx-background-color: transparent;");
        return root;
    }

    /**
     * Creates a scene whose content scales with the available window size.
     *
     * @param contentRoot root node to display
     * @return the responsive scene
     */
    private Scene createResponsiveScene(StackPane contentRoot) {
        StackPane viewport = new StackPane(contentRoot);
        viewport.setAlignment(Pos.CENTER);
        if (mainBackground != null) {
            viewport.setBackground(mainBackground);
        } else {
            viewport.setStyle("-fx-background-color: linear-gradient(to bottom right, #2a403d, #14221f);");
        }

        Scene scene = new Scene(viewport, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
        java.net.URL hiddenScrollCss = getClass().getResource("/ui/hidden-scroll.css");
        if (hiddenScrollCss != null) {
            scene.getStylesheets().add(hiddenScrollCss.toExternalForm());
        }
        DoubleBinding scale = Bindings.createDoubleBinding(
                () -> Math.min(scene.getWidth() / DESIGN_WINDOW_WIDTH, scene.getHeight() / DESIGN_WINDOW_HEIGHT),
                scene.widthProperty(),
                scene.heightProperty()
        );
        contentRoot.scaleXProperty().bind(scale);
        contentRoot.scaleYProperty().bind(scale);
        return scene;
    }

    /**
     * Switches scene while preserving fullscreen state.
     *
     * @param scene target scene.
     */
    private void switchScene(Scene scene) {
        switchScene(scene, false);
    }

    /**
     * Switches scene while preserving fullscreen state and optionally recenters windowed mode.
     *
     * @param scene target scene.
     * @param centerWhenWindowed true to center the window when not fullscreen.
     */
    private void switchScene(Scene scene, boolean centerWhenWindowed) {
        if (scene == null || window == null) {
            return;
        }

        boolean wasFullScreen = window.isFullScreen();
        window.setScene(scene);
        if (wasFullScreen) {
            Platform.runLater(() -> window.setFullScreen(true));
        } else if (centerWhenWindowed) {
            window.centerOnScreen();
        }
    }

    /**
     * Creates a reusable frosted-glass panel used across menu scenes.
     *
     * @return styled panel container.
     */
    private VBox createFrostedGlassPanel() {
        VBox panel = new VBox(30);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(50));
        panel.setMaxWidth(500);
        panel.setMaxHeight(600);
        panel.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-background-radius: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 20, 0, 0, 10); -fx-border-color: rgba(255,255,255,0.3); -fx-border-width: 2; -fx-border-radius: 20;");
        return panel;
    }

    /**
     * Builds a themed action button with hover feedback.
     *
     * @param text button caption.
     * @param primary if true, uses a brighter highlight style.
     * @return configured button instance.
     */
    private Button createMenuButton(String text, boolean primary) {
        Button btn = new Button(text.toUpperCase());
        String baseColor = primary ? "#b35930" : "#2a1208";
        String hoverColor = primary ? "#c46a41" : "#3b1a0c";
        String textColor = primary ? "#ffe0b2" : "#d99632";
        
        btn.setStyle("-fx-background-color: linear-gradient(to bottom, " + baseColor + ", #1a0a04); " +
                    "-fx-text-fill: " + textColor + "; -fx-font-family: '" + MENU_FONT + "', serif; " +
                    "-fx-font-weight: bold; -fx-font-size: 28px; -fx-background-radius: 8; -fx-padding: 15 80; " +
                    "-fx-border-color: #4a1906; -fx-border-width: 2; -fx-border-radius: 8; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 12, 0, 0, 6);");
        
        btn.setMinWidth(480);
        btn.setMinHeight(72);
        
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(baseColor, hoverColor)));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(hoverColor, baseColor)));
        return btn;
    }

    /**
     * Creates a compact button using the shared menu style.
     *
     * @param text button label
     * @return the styled button
     */
    private Button createSmallMenuButton(String text) {
        Button btn = new Button(text.toUpperCase());
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #d99632; -fx-font-family: '" + MENU_FONT + "', serif; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 10 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0.4, 0, 1);");
        btn.setOnMouseEntered(e -> btn.setTextFill(Color.web("#f4c762")));
        btn.setOnMouseExited(e -> btn.setTextFill(Color.web("#d99632")));
        return btn;
    }

    /** Builds the connection settings scene. */
    private void buildConnectionScene() {
        StackPane root = createRootWithBackground();
        Region shade = new Region();
        shade.setStyle("-fx-background-color: radial-gradient(center 50% 42%, radius 76%, rgba(15,6,3,0.2), rgba(5,4,5,0.68));");
        root.getChildren().add(shade);

        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(50));

        Text logo = new Text("MESOS");
        logo.setFont(Font.font(TITLE_FONT, FontWeight.NORMAL, 86));
        logo.setFill(Color.web("#fff3d4"));
        logo.setStyle("-fx-effect: dropshadow(gaussian, rgba(45,8,2,0.95), 7, 0.45, 0, 4);");

        Label title = new Label("SERVER SETTINGS");
        title.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 34));
        title.setTextFill(Color.web("#f4c762"));

        VBox panel = new VBox(30);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(600);
        panel.setPadding(new Insets(40));
        panel.setStyle("-fx-background-color: rgba(20,11,7,0.86); -fx-background-radius: 15; -fx-border-color: #a66a2b; -fx-border-width: 3; -fx-border-radius: 15;");

        GridPane form = new GridPane();
        form.setHgap(30); form.setVgap(25); form.setAlignment(Pos.CENTER);

        protocolCombo = new ComboBox<>();
        protocolCombo.getItems().addAll("Socket", "Rmi");
        protocolCombo.setValue("rmi".equalsIgnoreCase(NetworkSettings.getProtocolFromJSON()) ? "Rmi" : "Socket");
        styleInput(protocolCombo);

        hostField = new TextField(NetworkSettings.getHostFromJSON());
        styleInput(hostField);
        
        portField = new TextField(String.valueOf(NetworkSettings.getPortFromJSON()));
        styleInput(portField);
        
        bindingField = new TextField(NetworkSettings.getRmiBindingNameFromJSON());
        styleInput(bindingField);
        bindingField.setDisable(!"Rmi".equalsIgnoreCase(protocolCombo.getValue()));
        protocolCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            boolean rmi = "Rmi".equalsIgnoreCase(newValue);
            portField.setText(String.valueOf(rmi ? NetworkSettings.getRmiPortFromJSON() : NetworkSettings.getPortFromJSON()));
            bindingField.setDisable(!rmi);
        });
        
        addStyledFormRow(form, "Protocol:", protocolCombo, 0);
        addStyledFormRow(form, "Host:", hostField, 1);
        addStyledFormRow(form, "Port:", portField, 2);
        addStyledFormRow(form, "RMI Binding:", bindingField, 3);

        HBox buttons = new HBox(40);
        buttons.setAlignment(Pos.CENTER);

        Button backBtn = createSmallMenuButton("BACK");
        backBtn.setOnAction(e -> {
            switchScene(mainMenuScene);
        });

        connectButton = createMenuButton("CONNECT", true);
        connectButton.setMinWidth(300);
        connectButton.setOnAction(e -> performNetworkConnection());

        buttons.getChildren().addAll(backBtn, connectButton);

        panel.getChildren().addAll(form, buttons);
        layout.getChildren().addAll(logo, title, panel);
        root.getChildren().add(layout);
        connectionScene = createResponsiveScene(root);
    }

    /** Builds the Hub menu scene. */
    private void buildMainMenuScene() {
        StackPane root = createRootWithBackground();
        Region shade = new Region();
        shade.setStyle("-fx-background-color: radial-gradient(center 50% 42%, radius 76%, rgba(15,6,3,0.2), rgba(5,4,5,0.68));");
        root.getChildren().add(shade);

        VBox layout = new VBox(40);
        layout.setAlignment(Pos.CENTER);

        Text logo = new Text("MESOS");
        logo.setFont(Font.font(TITLE_FONT, FontWeight.NORMAL, 100));
        logo.setFill(Color.web("#fff3d4"));
        logo.setStyle("-fx-effect: dropshadow(gaussian, rgba(45,8,2,0.95), 10, 0.45, 0, 6);");

        Text subtitle = new Text("WRITE THE LEGEND. LEAD YOUR PEOPLE.");
        subtitle.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 24));
        subtitle.setFill(Color.web("#f4c762"));
        subtitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 5, 0.5, 0, 2);");

        VBox buttonBox = new VBox(25);
        buttonBox.setAlignment(Pos.CENTER);

        Button createBtn = createMenuButton("CREATE MATCH", true);
        createBtn.setOnAction(e -> switchScene(createScene));

        Button joinBtn = createMenuButton("JOIN MATCH", true);
        joinBtn.setOnAction(e -> switchScene(joinScene));

        Button reconnectBtn = createMenuButton("RECONNECT", true);
        reconnectBtn.setOnAction(e -> switchScene(reconnectScene));

        buttonBox.getChildren().addAll(createBtn, joinBtn, reconnectBtn);

        HBox footer = new HBox(60);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(20, 0, 0, 0));

        Button settingsBtn = createMenuButton("SETTINGS", false);
        settingsBtn.setMinWidth(220);
        settingsBtn.setOnAction(e -> switchScene(connectionScene));

        Button quitBtn = createMenuButton("QUIT", false);
        quitBtn.setMinWidth(220);
        quitBtn.setOnAction(e -> Platform.exit());

        footer.getChildren().addAll(settingsBtn, quitBtn);

        layout.getChildren().addAll(logo, subtitle, buttonBox, footer);
        root.getChildren().add(layout);
        mainMenuScene = createResponsiveScene(root);
    }

    /** Builds the reusable scenes used to submit game actions. */
    private void buildActionScenes() {
        createScene = buildInputScene("CREATE MATCH", "CREATE & ENTER", panel -> {
            GridPane form = new GridPane();
            form.setHgap(30); form.setVgap(20); form.setAlignment(Pos.CENTER);
            createNickField = new TextField(); styleInput(createNickField);
            createNickField.setPromptText("(Your legendary name!)");
            applyValidation(createNickField, 12, false);
            
            createRoomField = new TextField(); styleInput(createRoomField);
            createRoomField.setPromptText("(Numbers only)");
            applyValidation(createRoomField, 4, true);
            
            createPinField = new TextField(); styleInput(createPinField);
            createPinField.setPromptText("(For reconnection)");
            applyValidation(createPinField, 12, false);

            createPlayersCombo = new ComboBox<>();
            createPlayersCombo.getItems().addAll(2, 3, 4, 5); createPlayersCombo.setValue(2);
            styleInput(createPlayersCombo);
            addStyledFormRow(form, "Nickname:", createNickField, 0);
            addStyledFormRow(form, "Personal PIN:", createPinField, 1);
            addStyledFormRow(form, "Room ID:", createRoomField, 2);
            addStyledFormRow(form, "Players:", createPlayersCombo, 3);
            panel.getChildren().add(form);
        }, e -> {
            try {
                performLogin(
                        "CREATE_ROOM",
                        createNickField.getText().trim(),
                        Integer.parseInt(createRoomField.getText().trim()),
                        createPinField.getText().trim(),
                        createPlayersCombo.getValue()
                );
            } catch (NumberFormatException ex) { showAlert("Room ID must be a number"); }
        });

        joinScene = buildInputScene("JOIN MATCH", "JOIN & ENTER", panel -> {
            GridPane form = new GridPane();
            form.setHgap(30); form.setVgap(20); form.setAlignment(Pos.CENTER);
            joinNickField = new TextField(); styleInput(joinNickField);
            joinNickField.setPromptText("(Your legendary name!)");
            applyValidation(joinNickField, 12, false);

            joinRoomField = new TextField(); styleInput(joinRoomField);
            joinRoomField.setPromptText("(Where is your next battle?)");
            applyValidation(joinRoomField, 4, true);

            joinPinField = new TextField(); styleInput(joinPinField);
            joinPinField.setPromptText("(For reconnection)");
            applyValidation(joinPinField, 12, false);

            addStyledFormRow(form, "Nickname:", joinNickField, 0);
            addStyledFormRow(form, "Personal PIN:", joinPinField, 1);
            addStyledFormRow(form, "Room ID:", joinRoomField, 2);
            panel.getChildren().add(form);
        }, e -> {
            try {
                performLogin("JOIN_ROOM", joinNickField.getText().trim(), Integer.parseInt(joinRoomField.getText().trim()), joinPinField.getText().trim(), 0);
            } catch (NumberFormatException ex) { showAlert("Room ID must be a number"); }
        });

        reconnectScene = buildInputScene("RECONNECT", "RECONNECT", panel -> {
            GridPane form = new GridPane();
            form.setHgap(30); form.setVgap(20); form.setAlignment(Pos.CENTER);
            reconnectNickField = new TextField(); styleInput(reconnectNickField);
            reconnectNickField.setPromptText("(Have we met before?)");
            applyValidation(reconnectNickField, 12, false);

            reconnectRoomField = new TextField(); styleInput(reconnectRoomField);
            reconnectRoomField.setPromptText("(WHere was your last battle?)");
            applyValidation(reconnectRoomField, 4, true);

            reconnectPinField = new TextField(); styleInput(reconnectPinField);
            reconnectPinField.setPromptText("(Original PIN)");
            applyValidation(reconnectPinField, 12, false);

            addStyledFormRow(form, "Nickname:", reconnectNickField, 0);
            addStyledFormRow(form, "Recovery PIN:", reconnectPinField, 1);
            addStyledFormRow(form, "Room ID:", reconnectRoomField, 2);
            panel.getChildren().add(form);
        }, e -> {
            try {
                performLogin("RECONNECT_ROOM", reconnectNickField.getText().trim(), Integer.parseInt(reconnectRoomField.getText().trim()), reconnectPinField.getText().trim(), 0);
            } catch (NumberFormatException ex) { showAlert("Room ID must be a number"); }
        });
    }

    /**
     * Builds a standard form scene for a client action.
     *
     * @param titleText scene title
     * @param actionText submit-button label
     * @param formBuilder callback that populates the form
     * @param actionHandler submit-button handler
     * @return the configured scene
     */
    private Scene buildInputScene(String titleText, String actionText, Consumer<VBox> formBuilder, javafx.event.EventHandler<javafx.event.ActionEvent> actionHandler) {
        StackPane root = createRootWithBackground();
        Region shade = new Region();
        shade.setStyle("-fx-background-color: radial-gradient(center 50% 42%, radius 76%, rgba(15,6,3,0.2), rgba(5,4,5,0.68));");
        root.getChildren().add(shade);

        VBox layout = new VBox(30);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(50));

        Text logo = new Text("MESOS");
        logo.setFont(Font.font(TITLE_FONT, FontWeight.NORMAL, 70));
        logo.setFill(Color.web("#fff3d4"));

        Label title = new Label(titleText);
        title.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 40));
        title.setTextFill(Color.web("#f4c762"));

        VBox panel = new VBox(40);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(600);
        panel.setPadding(new Insets(50));
        panel.setStyle("-fx-background-color: rgba(20,11,7,0.86); -fx-background-radius: 15; -fx-border-color: #a66a2b; -fx-border-width: 3; -fx-border-radius: 15;");

        formBuilder.accept(panel);

        BorderPane bottomBar = new BorderPane();
        bottomBar.setPadding(new Insets(10, 0, 0, 0));

        Button backBtn = createSmallMenuButton("BACK");
        backBtn.setOnAction(e -> switchScene(mainMenuScene));
        bottomBar.setLeft(backBtn);

        Button actionBtn = createMenuButton(actionText, true);
        actionBtn.setMinWidth(280);
        actionBtn.setMinHeight(60);
        actionBtn.setFont(Font.font(MENU_FONT, FontWeight.BOLD, 22));
        actionBtn.setOnAction(actionHandler);
        bottomBar.setRight(actionBtn);

        panel.getChildren().add(bottomBar);
        layout.getChildren().addAll(logo, title, panel);
        root.getChildren().add(layout);
        return createResponsiveScene(root);
    }

    /**
     * Applies real-time validation to a TextField.
     * @param maxLen maximum character length.
     * @param digitsOnly if true, restricts input to numbers only.
     */
    private void applyValidation(TextField field, int maxLen, boolean digitsOnly) {
        field.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.length() > maxLen) return null;
            if (digitsOnly && !newText.matches("\\d*")) return null;
            return change;
        }));
    }

    /**
     * Displays an error alert to the user.
     *
     * @param message alert body
     */
    private void showAlert(String message) {
        Platform.runLater(() -> {
            Scene currentScene = window.getScene();
            if (currentScene == null || !(currentScene.getRoot() instanceof StackPane)) {
                // Fallback to native dialog if UI structure is unexpected
                Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
                alert.showAndWait();
                return;
            }

            StackPane root = (StackPane) currentScene.getRoot();
            
            StackPane overlay = new StackPane();
            overlay.setStyle("-fx-background-color: rgba(12,6,3,0.68);");
            overlay.setOnMouseClicked(e -> e.consume()); // Block clicks through the overlay

            VBox alertBox = new VBox(25);
            alertBox.setAlignment(Pos.CENTER);
            alertBox.setMaxWidth(420);
            alertBox.setPadding(new Insets(40));
            alertBox.setStyle("-fx-background-color: rgba(45,22,10,0.98); -fx-background-radius: 15; -fx-border-color: #a66a2b; -fx-border-width: 3; -fx-border-radius: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 20, 0, 0, 10);");

            Text title = new Text("ATTENTION");
            title.setFont(Font.font(TITLE_FONT, 38));
            title.setFill(Color.web("#e53e3e")); // Reddish alert color
            title.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.5, 0, 1);");

            Label msgLabel = new Label(message.toUpperCase());
            msgLabel.setWrapText(true);
            msgLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            msgLabel.setTextFill(Color.web("#fff0c2"));
            msgLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));

            Button okBtn = new Button("UNDERSTOOD");
            styleGameActionButton(okBtn, true);
            okBtn.setMinWidth(220);
            okBtn.setOnAction(e -> root.getChildren().remove(overlay));

            alertBox.getChildren().addAll(title, msgLabel, okBtn);
            overlay.getChildren().add(alertBox);
            root.getChildren().add(overlay);
            overlay.toFront();
        });
    }

    /**
     * Creates a button styled for the settings panel.
     *
     * @param text button label
     * @return the styled button
     */
    private Button createSettingsActionButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(300);
        button.setMinHeight(58);
        String baseStyle = "-fx-background-color: linear-gradient(to bottom, #b94f19, #7c260d); -fx-text-fill: #ffe9b8; -fx-font-family: '" + MENU_FONT + "', '" + LABEL_FONT + "', serif; -fx-font-weight: bold; -fx-font-size: 28px; -fx-background-radius: 7; -fx-padding: 8 24; -fx-border-color: #461707; -fx-border-width: 3; -fx-border-radius: 7; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.82), 8, 0, 0, 5);";
        String hoverStyle = baseStyle.replace("#b94f19", "#d66520").replace("#7c260d", "#94320f");
        button.setStyle(baseStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
        return button;
    }

    /**
     * Applies the shared form style to an input control.
     *
     * @param control control to style
     */
    private void styleInput(Control control) {
        control.setMinWidth(280);
        control.setStyle("-fx-background-color: rgba(12,7,4,0.86); -fx-text-fill: #fff0c8; -fx-prompt-text-fill: #b89161; -fx-border-color: rgba(185,111,39,0.82); -fx-border-width: 2; -fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 12 14; -fx-font-family: '" + LABEL_FONT + "', serif; -fx-font-size: 16px;");
        if (control instanceof ComboBox<?>) {
            @SuppressWarnings("rawtypes")
            ComboBox comboBox = (ComboBox) control;
            comboBox.setButtonCell(new ListCell<Object>() {
                @Override
                /** {@inheritDoc} */
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.toString());
                    setTextFill(Color.web("#fff0c8"));
                    setStyle("-fx-background-color: transparent; -fx-font-family: '" + LABEL_FONT + "', serif; -fx-font-size: 16px;");
                }
            });
            comboBox.setCellFactory(listView -> new ListCell<Object>() {
                @Override
                /** {@inheritDoc} */
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.toString());
                    setTextFill(Color.web("#fff0c8"));
                    setStyle("-fx-background-color: rgba(12,7,4,0.96); -fx-font-family: '" + LABEL_FONT + "', serif; -fx-font-size: 16px;");
                }
            });
        }
    }

    /**
     * Adds one labeled and styled control to a form grid.
     *
     * @param form target form
     * @param labelText field label
     * @param control input control
     * @param row target row index
     */
    private void addStyledFormRow(GridPane form, String labelText, Control control, int row) {
        Label label = new Label(labelText);
        label.setTextFill(Color.web("#f4c762"));
        label.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 17));
        label.setStyle("-fx-effect: dropshadow(gaussian, rgba(31,7,2,0.92), 3, 0.45, 0, 1);");
        form.add(label, 0, row);
        form.add(control, 1, row);
    }

    /** Builds the main in-game scene and dashboard widgets. */
    private void buildGameScene() {
        StackPane root = createRootWithBackground();

        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: radial-gradient(center 50% 45%, radius 78%, rgba(0,0,0,0.18), rgba(2,1,1,0.72));");
        root.getChildren().add(overlay);

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(8, 22, 10, 22));
        root.getChildren().add(layout);

        // --- HEADER HUD RESTRUCTURE ---
        connectedPlayerLabel = createTopHudLabel("Player: -");
        connectedPlayerLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 16));
        connectedPlayerLabel.setMinWidth(0);
        connectedPlayerLabel.setMaxWidth(250);
        connectedPlayerLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        connectedPlayerTokenView = new ImageView();
        connectedPlayerTokenView.setFitWidth(58);
        connectedPlayerTokenView.setPreserveRatio(true);
        connectedPlayerTokenView.setSmooth(true);
        connectedPlayerTokenView.setManaged(false);
        connectedPlayerTokenView.setVisible(false);
        VBox topLeftBox = new VBox(7, connectedPlayerLabel, connectedPlayerTokenView);
        topLeftBox.setAlignment(Pos.TOP_LEFT);

        roundLabel = createTopHudLabel("Round: -");
        eraLabel = createTopHudLabel("Era: -");
        VBox topRightBox = new VBox(8, roundLabel, eraLabel);
        topRightBox.setAlignment(Pos.TOP_RIGHT);

        Text gameTitle = new Text("MESOS");
        gameTitle.setFont(Font.font(TITLE_FONT, FontWeight.NORMAL, 46));
        gameTitle.setFill(Color.web("#f6ecd1"));
        gameTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(10,4,1,0.95), 7, 0.45, 0, 3);");
        VBox titleBox = new VBox(gameTitle);
        titleBox.setAlignment(Pos.TOP_CENTER);
        titleBox.setPadding(new Insets(10, 0, 0, 0));
        gameHeader = titleBox;

        BorderPane headerPane = new BorderPane();
        headerPane.setPadding(new Insets(16, 24, 0, 24));
        headerPane.setLeft(topLeftBox);
        headerPane.setRight(topRightBox);
        StackPane titleLayer = new StackPane(headerPane, titleBox);
        titleLayer.setAlignment(Pos.TOP_CENTER);
        StackPane.setAlignment(titleBox, Pos.TOP_CENTER);
        layout.setTop(titleLayer);

        turnInfoBox = new VBox(7);
        turnInfoBox.setAlignment(Pos.TOP_CENTER);
        turnInfoBox.setPadding(new Insets(88, 6, 0, 0));
        turnInfoBox.setPrefWidth(220);
        turnInfoBox.setManaged(false);
        turnInfoBox.setVisible(false);

        Label turnTitle = createSidePanelTitle("Turn");
        turnOwnerLabel = new Label("Waiting");
        turnOwnerLabel.setWrapText(true);
        turnOwnerLabel.setTextFill(Color.web("#fff0c2"));
        turnOwnerLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 19));
        turnOwnerLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 4, 0.5, 0, 1);");

        turnInstructionLabel = new Label("Choose an offer tile");
        turnInstructionLabel.setWrapText(true);
        turnInstructionLabel.setMaxWidth(195);
        turnInstructionLabel.setMinHeight(42);
        turnInstructionLabel.setPrefHeight(42);
        turnInstructionLabel.setMaxHeight(42);
        turnInstructionLabel.setTextFill(Color.web("#f5d28a"));
        turnInstructionLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 14));
        turnInstructionLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 3, 0.5, 0, 1);");

        turnOrderTrackView = new ImageView();
        turnOrderTrackView.setFitWidth(124);
        turnOrderTrackView.setPreserveRatio(true);
        turnOrderTrackView.setSmooth(true);

        turnOrderTokenLayer = new Pane();
        turnOrderTokenLayer.setMouseTransparent(true);

        turnOrderTrackPane = new StackPane(turnOrderTrackView, turnOrderTokenLayer);
        turnOrderTrackPane.setAlignment(Pos.TOP_CENTER);
        turnOrderTrackPane.setManaged(false);
        turnOrderTrackPane.setVisible(false);
        turnOrderTrackPane.setOnMouseEntered(e -> setTurnOrderTrackHover(true));
        turnOrderTrackPane.setOnMouseExited(e -> setTurnOrderTrackHover(false));
        VBox.setMargin(turnOrderTrackPane, new Insets(34, 0, 0, 0));

        turnInfoBox.getChildren().addAll(turnTitle, turnOwnerLabel, turnInstructionLabel, turnOrderTrackPane);
        layout.setLeft(turnInfoBox);

        playersBox = new VBox(7);
        playersBox.setAlignment(Pos.TOP_LEFT);
        playersBox.setPadding(new Insets(88, 0, 0, 10));
        playersBox.setPrefWidth(250);
        playersBox.setManaged(false);
        playersBox.setVisible(false);
        layout.setRight(playersBox);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setManaged(false);
        logArea.setVisible(false);

        VBox tableArea = new VBox(8);
        tableArea.setAlignment(Pos.CENTER);
        tableArea.setPadding(new Insets(0, 24, 14, 24));
        tableArea.setMinHeight(380);
        tableArea.setStyle("-fx-background-color: transparent;");
        tableArea.setManaged(false);
        tableArea.setVisible(false);
        gameplayArea = tableArea;
        BorderPane.setMargin(tableArea, new Insets(0, 4, 8, 4));

        upperTribeBox = new HBox(9); upperTribeBox.setAlignment(Pos.CENTER);
        upperBuildingBox = new HBox(9); upperBuildingBox.setAlignment(Pos.CENTER);
        lowerTribeBox = new HBox(9); lowerTribeBox.setAlignment(Pos.CENTER);
        lowerBuildingBox = new HBox(9); lowerBuildingBox.setAlignment(Pos.CENTER);
        HBox upperCardsArea = new HBox(14, upperTribeBox, upperBuildingBox);
        upperCardsArea.setAlignment(Pos.CENTER);
        HBox lowerCardsArea = new HBox(14, lowerTribeBox, lowerBuildingBox);
        lowerCardsArea.setAlignment(Pos.CENTER);

        Label upperRowTitle = createGameSectionTitle("Upper Row");
        Label offerTilesTitle = createGameSectionTitle("Offer Tiles");
        Label lowerRowTitle = createGameSectionTitle("Lower Row");
        VBox upperRowArea = new VBox(6, upperRowTitle, upperCardsArea);
        upperRowArea.setAlignment(Pos.CENTER);
        VBox lowerRowArea = new VBox(6, lowerRowTitle, lowerCardsArea);
        lowerRowArea.setAlignment(Pos.CENTER);

        offerTilesBox = new HBox(12);
        offerTilesBox.setAlignment(Pos.CENTER);
        StackPane offerTilesViewport = new StackPane(offerTilesBox);
        offerTilesViewport.setAlignment(Pos.CENTER);
        offerTilesViewport.setStyle("-fx-background-color: transparent;");
        offerTilesViewport.setMinHeight(tileButtonSize + 8);

        offerTilesScroll = new ScrollPane(offerTilesViewport);
        offerTilesScroll.setPannable(true);
        offerTilesScroll.setFitToWidth(true);
        offerTilesScroll.setFitToHeight(true);
        offerTilesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        offerTilesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        offerTilesScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-padding: 0;");
        offerTilesScroll.setBackground(Background.EMPTY);
        offerTilesScroll.setBorder(Border.EMPTY);
        offerTilesScroll.setMinHeight(tileButtonSize + 10);
        offerTilesScroll.setPrefViewportHeight(tileButtonSize + 8);
        offerTilesScroll.setMaxHeight(tileButtonSize + 12);
        offerTilesScroll.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                offerTilesViewport.setMinWidth(newBounds.getWidth()));

        VBox offerTilesArea = new VBox(0, offerTilesTitle, offerTilesScroll);
        offerTilesArea.setAlignment(Pos.CENTER);
        offerTilesArea.setMinHeight(tileButtonSize + 34);

        tableArea.getChildren().addAll(upperRowArea, offerTilesArea, lowerRowArea);

        placedTotemsBox = new VBox(8);
        placedTotemsBox.setManaged(false);
        placedTotemsBox.setVisible(false);
        showPlacedTotemsPlaceholder();

        StackPane bottomDash = new StackPane();
        bottomDash.setPadding(new Insets(4, 8, 2, 8));
        bottomDash.setStyle("-fx-background-color: transparent;");
        bottomDash.setManaged(false);
        bottomDash.setVisible(false);
        gameDashboard = bottomDash;

        HBox resourcesBox = new HBox(20);
        resourcesBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox foodBox = createResourcePlaque("Food", "🍗", "#e53e3e"); 
        foodValueLabel = (Label) foodBox.getChildren().get(1);
        
        VBox prestigeBox = createResourcePlaque("Prestige", "⭐", "#ecc94b"); 
        prestigeValueLabel = (Label) prestigeBox.getChildren().get(1);
        
        resourcesBox.getChildren().addAll(foodBox, prestigeBox);
        StackPane.setAlignment(resourcesBox, Pos.BOTTOM_LEFT);

        handBox = new HBox(10);
        handBox.setAlignment(Pos.CENTER_LEFT);
        handBox.setFillHeight(false);
        handBox.setMouseTransparent(true);
        Pane handViewport = new Pane(handBox);
        handViewport.setMinSize(620, 128);
        handViewport.setPrefSize(620, 128);
        handViewport.setMaxSize(620, 128);
        handViewport.setMouseTransparent(true);
        handViewport.setStyle("-fx-background-color: transparent;");
        Rectangle handClip = new Rectangle();
        handClip.widthProperty().bind(handViewport.widthProperty());
        handClip.heightProperty().bind(handViewport.heightProperty());
        handViewport.setClip(handClip);

        handExpandButton = new Button("Expand");
        stylePlayerHandButton(handExpandButton);
        handExpandButton.setMinWidth(76);
        handExpandButton.setMinHeight(38);
        handExpandButton.setVisible(false);
        handExpandButton.setManaged(false);
        handExpandButton.setOnAction(e -> showOwnHandOverlay());

        HBox handPreview = new HBox(12, handViewport, handExpandButton);
        handPreview.setAlignment(Pos.CENTER);
        handPreview.setMinHeight(128);
        handPreview.setPrefHeight(128);
        handPreview.setMaxHeight(128);

        VBox handArea = new VBox(12, createGameSectionTitle("Your Cards"), handPreview);
        handArea.setAlignment(Pos.CENTER);
        handArea.setMinWidth(720);
        handArea.setPrefWidth(720);
        handArea.setMaxWidth(720);
        StackPane.setAlignment(handArea, Pos.BOTTOM_CENTER);

        VBox controlsBox = new VBox(10);
        controlsBox.setAlignment(Pos.CENTER_RIGHT);
        controlsBox.setPickOnBounds(false);

        Button bottomQuitBtn = new Button("Quit");
        styleGameActionButton(bottomQuitBtn, false);
        bottomQuitBtn.setMinWidth(122);
        bottomQuitBtn.setMinHeight(38);
        bottomQuitBtn.setOnAction(e -> showInGameMenu());
        
        playersSelectionBox = new HBox(8);
        playersSelectionBox.setAlignment(Pos.CENTER_RIGHT);
        for (int players : new int[]{2, 3, 4, 5}) {
            Button playerButton = createPlayerCountButton(players);
            playerCountButtons.add(playerButton);
            playersSelectionBox.getChildren().add(playerButton);
        }
        refreshPlayerCountSelection();
        
        readyButton = new Button("Ready");
        styleGameActionButton(readyButton, true);
        readyButton.setOnAction(e -> {
            sendAction("ready");
            readyButton.setDisable(true);
        });

        removeReadyButton = new Button("Remove Ready");
        styleGameActionButton(removeReadyButton, false);
        removeReadyButton.setVisible(false);
        removeReadyButton.setManaged(false);
        removeReadyButton.setOnAction(e -> {
            sendAction("unready");
            removeReadyButton.setDisable(true);
        });
        
        StackPane.setAlignment(controlsBox, Pos.BOTTOM_RIGHT);
        controlsBox.getChildren().add(bottomQuitBtn);
        bottomDash.getChildren().addAll(resourcesBox, controlsBox, handArea);

        Text preStartTitle = new Text("MESOS");
        preStartTitle.setFont(Font.font(TITLE_FONT, FontWeight.NORMAL, 72));
        preStartTitle.setFill(Color.web("#f6ecd1"));
        preStartTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(10,4,1,0.95), 8, 0.45, 0, 4);");

        startPromptLabel = new Label("WAITING FOR PLAYERS");
        startPromptLabel.setTextFill(Color.web("#f5d28a"));
        startPromptLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 28));
        startPromptLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 5, 0.55, 0, 2);");

        readyStatusLabel = new Label("Ready players: 0");
        readyStatusLabel.setTextFill(Color.web("#f5d28a"));
        readyStatusLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
        readyStatusLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 4, 0.5, 0, 1);");

        colorSelectionLabel = new Label("");
        colorSelectionLabel.setTextFill(Color.web("#fff0c2"));
        colorSelectionLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 16));
        colorSelectionLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 4, 0.5, 0, 1);");
        colorSelectionLabel.setVisible(false);
        colorSelectionLabel.setManaged(false);

        colorSelectionBox = new HBox(16);
        colorSelectionBox.setAlignment(Pos.CENTER);
        colorSelectionBox.setVisible(false);
        colorSelectionBox.setManaged(false);

        HBox preStartButtons = new HBox(18, readyButton, removeReadyButton);
        preStartButtons.setAlignment(Pos.CENTER);

        preStartPane = new VBox(22, preStartTitle, startPromptLabel, readyStatusLabel, colorSelectionLabel, colorSelectionBox, preStartButtons);
        preStartPane.setAlignment(Pos.CENTER);
        preStartPane.setPadding(new Insets(0, 0, 70, 0));

        StackPane centerPane = new StackPane(gameplayArea, preStartPane);
        centerPane.setAlignment(Pos.CENTER);
        StackPane.setAlignment(gameplayArea, Pos.TOP_CENTER);
        layout.setCenter(centerPane);
        layout.setBottom(bottomDash);

        gatewayLobbyButton = new Button("Back to Lobby");
        styleGameActionButton(gatewayLobbyButton, false);
        gatewayLobbyButton.setMinWidth(172);
        gatewayLobbyButton.setMinHeight(42);
        gatewayLobbyButton.setOnAction(e -> returnToGatewayMenuFromRoom());
        StackPane.setAlignment(gatewayLobbyButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(gatewayLobbyButton, new Insets(0, 32, 28, 0));
        root.getChildren().add(gatewayLobbyButton);

        // --- OVERLAYS ---
        playerHandOverlay = new StackPane();
        playerHandOverlay.setVisible(false);
        playerHandOverlay.setManaged(false);
        playerHandOverlay.setAlignment(Pos.CENTER);
        playerHandOverlay.setPadding(new Insets(18, 60, 18, 60));
        playerHandOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.42);");
        playerHandOverlay.setOnMouseClicked(e -> hidePlayerHandOverlay());
        root.getChildren().add(playerHandOverlay);

        // ESSENTIAL: Initialize and add the in-game pause menu to the front
        buildInGameMenuOverlay();
        root.getChildren().add(inGameMenuOverlay);

        gameScene = createResponsiveScene(root);
    }

    /**
     * Creates a compact resource widget used in the player dashboard.
     *
     * @param title resource title.
     * @param icon resource icon text.
     * @param colorHex resource value color.
     * @return configured resource plaque.
     */
    private VBox createResourcePlaque(String title, String icon, String colorHex) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(100);
        box.setMaxWidth(112);
        box.setPrefHeight(82);
        box.setMaxHeight(82);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(34,18,8,0.96), rgba(11,6,3,0.96)); -fx-background-radius: 6; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.7), 8, 0.15, 0, 4);");
        
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web("#f1c66b"));
        titleLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 13));
        
        Label valueLabel = new Label(icon + " 0"); 
        valueLabel.setTextFill(Color.web(colorHex));
        valueLabel.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 22));
        
        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    /**
     * Creates a player-count selection button using the matching tile image when available.
     *
     * @param players number of players represented by the button.
     * @return configured player-count button.
     */
    private Button createPlayerCountButton(int players) {
        Image image = ImageLoader.getTileImage(players + "p.png");
        Button button = new Button();
        button.setMinSize(playerButtonSize, playerButtonSize);
        button.setMaxSize(playerButtonSize, playerButtonSize);
        button.setStyle("-fx-background-color: rgba(18,9,4,0.72); -fx-background-radius: 7; -fx-border-color: #6b3912; -fx-border-width: 2; -fx-border-radius: 7;");

        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(playerImageFitWidth);
            view.setPreserveRatio(true);
            button.setGraphic(view);
        } else {
            button.setText(players + "P");
            button.setTextFill(Color.web("#f5d28a"));
            button.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 16));
        }

        button.setOnAction(e -> {
            selectedPlayers = players;
            refreshPlayerCountSelection();
        });
        return button;
    }

    /**
     * Creates an offer-tile button with image, tooltip, and hover feedback.
     *
     * @param letter offer tile identifier.
     * @param tooltipText tooltip content describing the tile.
     * @param placedTotemColor color of the token to overlay, or null when the tile is empty.
     * @return configured offer-tile button.
     */
    private Button createOfferTileButton(String letter, String tooltipText, String placedTotemColor) {
        Image image = ImageLoader.getTileImage(letter.toLowerCase() + ".png");
        Button button = new Button();
        double buttonWidth = tileImageFitWidth + 6;
        button.setMinSize(buttonWidth, tileButtonSize + 4);
        button.setMaxSize(buttonWidth, tileButtonSize + 4);
        button.setPrefSize(buttonWidth, tileButtonSize + 4);
        button.setPadding(Insets.EMPTY);
        button.setFocusTraversable(false);
        refreshOfferTileButtonStyle(button);

        StackPane tileGraphic = new StackPane();
        tileGraphic.setAlignment(Pos.CENTER);

        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitHeight(tileButtonSize);
            view.setPreserveRatio(true);
            tileGraphic.getChildren().add(view);
        } else {
            Text fallback = new Text(letter);
            fallback.setFill(Color.web("#f5d28a"));
            fallback.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 24));
            tileGraphic.getChildren().add(fallback);
        }

        Image tokenImage = ImageLoader.getTokenImage(placedTotemColor);
        if (tokenImage != null) {
            ImageView tokenView = new ImageView(tokenImage);
            tokenView.setFitHeight(tileButtonSize * 0.48);
            tokenView.setPreserveRatio(true);
            tokenView.setSmooth(true);
            StackPane.setAlignment(tokenView, Pos.CENTER);
            tileGraphic.getChildren().add(tokenView);
            button.setDisable(true);
            button.setOpacity(1.0);
        }
        button.setGraphic(tileGraphic);

        if (tooltipText != null && !tooltipText.trim().isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setStyle("-fx-background-color: rgba(20,10,4,0.94); -fx-text-fill: #fff1c8; -fx-font-size: 14px; -fx-border-color: #8b501b; -fx-border-width: 2; -fx-border-radius: 5;");
            Tooltip.install(button, tooltip);
        }

        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-effect: dropshadow(three-pass-box, rgba(240,160,45,0.7), 16, 0.45, 0, 0);"));
        button.setOnMouseExited(e -> refreshOfferTileButtonStyle(button));
        return button;
    }

    /**
     * Creates a selectable color token button for the pre-game color phase.
     *
     * @param colorName color command payload.
     * @param enabled whether the local player can choose this color now.
     * @return configured token button.
     */
    private Button createColorTokenButton(String colorName, boolean enabled) {
        Image image = ImageLoader.getTokenFrontImage(colorName);
        Button button = new Button();
        button.setMinSize(82, 96);
        button.setMaxSize(82, 96);
        button.setPrefSize(82, 96);
        button.setPadding(Insets.EMPTY);
        button.setFocusTraversable(false);
        button.setDisable(!enabled);
        button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; " +
                "-fx-padding: 0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.72), 9, 0.22, 0, 4);");

        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(72);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            button.setGraphic(view);
        } else {
            button.setText(colorName);
            button.setTextFill(Color.web("#f5d28a"));
            button.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 12));
        }

        Tooltip.install(button, new Tooltip(colorName));
        button.setOnAction(e -> {
            sendAction("color " + colorName);
            button.setDisable(true);
        });
        if (enabled) {
            button.setOnMouseEntered(e -> {
                button.setScaleX(1.08);
                button.setScaleY(1.08);
            });
            button.setOnMouseExited(e -> {
                button.setScaleX(1.0);
                button.setScaleY(1.0);
            });
        } else {
            button.setOpacity(0.72);
        }
        return button;
    }

    /**
     * Restores the default visual style for an offer-tile button.
     *
     * @param button button to restyle.
     */
    private void refreshOfferTileButtonStyle(Button button) {
        button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.62), 8, 0.18, 0, 4);");
    }

    /** Refreshes the visual selected state of all player-count buttons. */
    private void refreshPlayerCountSelection() {
        for (Button button : playerCountButtons) {
            boolean selected = false;
            if (button.getGraphic() instanceof ImageView imageView && imageView.getImage() != null) {
                String url = imageView.getImage().getUrl();
                selected = url != null && url.contains(selectedPlayers + "p.png");
            } else if (button.getText() != null) {
                selected = button.getText().startsWith(String.valueOf(selectedPlayers));
            }

            if (selected) {
                button.setStyle("-fx-background-color: rgba(58,27,8,0.82); -fx-background-radius: 7; -fx-border-color: #ecc94b; -fx-border-width: 3; -fx-border-radius: 7;");
            } else {
                button.setStyle("-fx-background-color: rgba(18,9,4,0.72); -fx-background-radius: 7; -fx-border-color: #6b3912; -fx-border-width: 2; -fx-border-radius: 7;");
            }
        }
    }

    /**
     * Creates a small carved-board section caption for the game scene.
     *
     * @param text caption text.
     * @return configured label.
     */
    private Label createGameSectionTitle(String text) {
        Label label = new Label("◇  " + text + "  ◇");
        label.setTextFill(Color.web("#f0bf63"));
        label.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
        label.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.45, 0, 1);");
        return label;
    }

    /**
     * Creates a small side caption for gameplay metadata.
     *
     * @param text caption text.
     * @return configured label.
     */
    private Label createSidePanelTitle(String text) {
        Label label = new Label(text.toUpperCase());
        label.setTextFill(Color.web("#f0bf63"));
        label.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 16));
        label.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 3, 0.5, 0, 1);");
        return label;
    }

    /**
     * Applies the in-game carved button style.
     *
     * @param button target button.
     * @param primary true for the stronger start-game action.
     */
    private void styleGameActionButton(Button button, boolean primary) {
        String top = primary ? "#d77c24" : "#6a2d14";
        String bottom = primary ? "#8f350f" : "#2d1309";
        String text = primary ? "#fff0c2" : "#dfbd82";
        String border = primary ? "#4d1a06" : "#120806";
        button.setMinWidth(138);
        button.setMinHeight(primary ? 46 : 40);
        button.setStyle("-fx-background-color: linear-gradient(to bottom, " + top + ", " + bottom + "); -fx-text-fill: " + text + "; -fx-font-family: '" + MENU_FONT + "', '" + LABEL_FONT + "', serif; -fx-font-weight: bold; -fx-font-size: 17px; -fx-background-radius: 7; -fx-padding: 8 18; -fx-border-color: " + border + "; -fx-border-width: 2; -fx-border-radius: 7; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.78), 7, 0, 0, 4);");
    }

    /**
     * Creates a label for the top heads-up display.
     *
     * @param text initial label text
     * @return the styled label
     */
    private Label createTopHudLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(180);
        label.setTextFill(Color.web("#fff0c2"));
        label.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 20));
        label.setStyle("-fx-background-color: rgba(17,8,4,0.68); -fx-background-radius: 7; -fx-padding: 8 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.82), 5, 0.45, 0, 2);");
        return label;
    }

    /** Switches from the centered pre-start screen to the full gameplay layout. */
    private void showGameplayLayout() {
        if (preStartPane != null) {
            preStartPane.setVisible(false);
            preStartPane.setManaged(false);
        }
        if (gatewayLobbyButton != null) {
            gatewayLobbyButton.setVisible(false);
            gatewayLobbyButton.setManaged(false);
        }
        if (gameHeader != null) {
            gameHeader.setVisible(true);
            gameHeader.setManaged(true);
        }
        if (gameplayArea != null) {
            gameplayArea.setVisible(true);
            gameplayArea.setManaged(true);
        }
        if (gameDashboard != null) {
            gameDashboard.setVisible(true);
            gameDashboard.setManaged(true);
        }
        if (turnInfoBox != null) {
            turnInfoBox.setVisible(true);
            turnInfoBox.setManaged(true);
        }
        if (playersBox != null) {
            playersBox.setVisible(true);
            playersBox.setManaged(true);
        }
    }

    /**
     * Adjust UI element sizes for smaller screens so bottom controls remain reachable.
     * @param height current window height
     */
    private void adjustLayoutForHeight(double height) {
        boolean compact = height < 700;
        if (compact) {
            tileButtonSize = 104;
            tileImageFitWidth = 64;
            playerButtonSize = 52;
            playerImageFitWidth = 44;
        } else {
            tileButtonSize = 126;
            tileImageFitWidth = 78;
            playerButtonSize = 72;
            playerImageFitWidth = 60;
        }

        if (offerTilesScroll != null) {
            offerTilesScroll.setMinHeight(tileButtonSize + (compact ? 8 : 10));
            offerTilesScroll.setPrefViewportHeight(tileButtonSize + (compact ? 6 : 8));
            offerTilesScroll.setMaxHeight(tileButtonSize + (compact ? 10 : 12));
        }
        if (turnOrderTrackView != null) {
            turnOrderTrackView.setFitWidth(compact ? 104 : 124);
        }

        // Update player buttons
        for (Button b : playerCountButtons) {
            b.setMinSize(playerButtonSize, playerButtonSize);
            b.setMaxSize(playerButtonSize, playerButtonSize);
            if (b.getGraphic() instanceof ImageView iv && iv.getImage() != null) {
                iv.setFitWidth(playerImageFitWidth);
            }
        }

        // Update offer tiles
        if (offerTilesBox != null) {
            for (javafx.scene.Node n : offerTilesBox.getChildren()) {
                if (n instanceof Button btn) {
                    double buttonWidth = tileImageFitWidth + 6;
                    btn.setMinSize(buttonWidth, tileButtonSize + 4);
                    btn.setMaxSize(buttonWidth, tileButtonSize + 4);
                    btn.setPrefSize(buttonWidth, tileButtonSize + 4);
                    if (btn.getGraphic() instanceof ImageView iv && iv.getImage() != null) {
                        iv.setFitHeight(tileButtonSize);
                    }
                }
            }
        }
    }

    // =========================================================================
    // NETWORKING & LOGIC
    // =========================================================================

    /**
     * Starts a connection attempt using the values from the join form.
     * Handles both socket and RMI modes but DOES NOT login yet.
     */
    private void performNetworkConnection() {
        String selectedProtocol = protocolCombo.getValue();
        String protocol = "rmi".equalsIgnoreCase(selectedProtocol) ? "Rmi" : "Socket";
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String bindingName = bindingField.getText().trim();

        connectButton.setDisable(true);
        connectButton.setText("CONNECTING...");

        connectionExecutor.submit(() -> {
            try {
                if ("rmi".equalsIgnoreCase(protocol)) {
                    int effectivePort = port;
                    int socketDefault = NetworkSettings.getPortFromJSON();
                    int rmiDefault = NetworkSettings.getRmiPortFromJSON();
                    if (effectivePort == socketDefault) {
                        final int corrected = rmiDefault;
                        Platform.runLater(() -> appendLog("Warning: selected RMI protocol but port matches socket default; switching to RMI port " + corrected));
                        effectivePort = corrected;
                    }

                    Registry registry = LocateRegistry.getRegistry(host, effectivePort);
                    GameServiceRemote service = (GameServiceRemote) registry.lookup(bindingName);
                    
                    RmiConnectionContext context = new RmiConnectionContext(service, null, "");
                    activeConnection.set(context);

                } else {
                    System.out.println("Attempting Socket connection to " + host + ":" + port);
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 5000);
                    System.out.println("Socket connected to " + host + ":" + port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    SocketConnectionContext context = new SocketConnectionContext(socket, out, "");

                    ClientThread listener = new ClientThread(in, null, update -> {
                        if (update != null) {
                            System.out.println("Socket update received: type=" + update.getType()
                                    + ", success=" + update.isSuccess()
                                    + ", content=" + update.getContent());
                            Platform.runLater(() -> {
                                try {
                                    handleIncomingUpdate(update);
                                } catch (Exception uiError) {
                                    System.err.println("Failed to handle server update in JavaFX UI: " + uiError);
                                    uiError.printStackTrace(System.err);
                                    showAlert("GUI update error: " + uiError.getClass().getSimpleName()
                                            + " - " + uiError.getMessage());
                                }
                            });
                        }
                    });
                    listener.setDaemon(true);
                    context.listener = listener;
                    activeConnection.set(context);
                    listener.start();
                }

                Platform.runLater(() -> {
                    if (mainMenuScene != null) {
                        switchScene(mainMenuScene, true);
                    }
                    resetLoginButton();
                    appendLog("Connected successfully to " + host + ":" + port);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String message = "Connection failed (" + protocol + " " + host + ":" + port + "): "
                            + e.getClass().getSimpleName() + " - " + e.getMessage();
                    Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
                    alert.showAndWait();
                    resetLoginButton();
                });
                disconnectSilently();
            }
        });
    }

    /**
     * Executes the login logic (Create, Join, Reconnect) via the active connection.
     * 
     * @param type Action type (e.g. "CREATE_ROOM", "JOIN_ROOM")
     * @param nickname Player nickname
     * @param roomId Room ID
     * @param pin Secret PIN
     * @param maxPlayers requested player count for CREATE_ROOM
     */
    private void performLogin(String type, String nickname, int roomId, String pin, int maxPlayers) {
        ConnectionContext context = activeConnection.get();
        if (context == null || !context.isConnected()) {
            showAlert("Connect to the server before entering a match.");
            switchScene(connectionScene);
            return;
        }
        if (nickname == null || nickname.isBlank()) {
            showAlert("Nickname cannot be empty");
            return;
        }
        if (pin == null || pin.isBlank()) {
            showAlert("Secret PIN cannot be empty");
            return;
        }
        lastLoginNickname = nickname;
        lastLoginPin = pin;
        lastLoginRoomId = roomId;

        connectionExecutor.submit(() -> {
            try {
                if (context instanceof RmiConnectionContext) {
                    RmiConnectionContext rmiCtx = (RmiConnectionContext) context;
                    GameServiceRemote service = rmiCtx.service;
                    resetPreStartControls();
                    
                    RmiCallback callback = new RmiCallback(update -> {
                        if (update != null) {
                            Platform.runLater(() -> handleIncomingUpdate(update));
                        }
                    });
                    
                    RmiLoginResponse loginResponse = service.login(new RmiLoginRequest(type, nickname, roomId, pin, maxPlayers), callback);
                    
                    if (!loginResponse.isSuccess()) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Login rejected: " + loginResponse.getMessage(), ButtonType.OK);
                            alert.showAndWait();
                        });
                        return;
                    }
                    
                    rmiCtx.nickname = nickname;
                    rmiCtx.callback = callback;
                    rmiCtx.startHeartbeat();
                    
                    Platform.runLater(() -> {
                        switchScene(gameScene, true);
                        appendLog(loginResponse.getMessage());
                    });
                } else {
                    SocketConnectionContext sockCtx = (SocketConnectionContext) context;
                    sockCtx.nickname = nickname;
                    resetPreStartControls();
                    System.out.println("Sending socket login: type=" + type
                            + ", nickname=" + nickname
                            + ", roomId=" + roomId
                            + ", players=" + maxPlayers);
                    sockCtx.send(new ActionMessage(type, nickname, 0, roomId, pin, maxPlayers));
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Login exception: " + e.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                });
            }
        });
    }

    /** Restores the connect button to its default enabled state. */
    private void resetLoginButton() {
        connectButton.setDisable(false);
        connectButton.setText("CONNECT");
    }

    /** Prefills reconnect fields with the current session details. */
    private void prefillReconnectForm() {
        if (lastLoginNickname != null && reconnectNickField != null) {
            reconnectNickField.setText(lastLoginNickname);
        }
        if (lastLoginPin != null && reconnectPinField != null) {
            reconnectPinField.setText(lastLoginPin);
        }
        if (lastLoginRoomId > 0 && reconnectRoomField != null) {
            reconnectRoomField.setText(String.valueOf(lastLoginRoomId));
        }
    }

    /**
     * Sends a textual command to the active server connection.
     *
     * @param command command to send.
     */
    private void sendAction(String command) {
        ConnectionContext context = activeConnection.get();
        if (context == null || !context.isConnected()) return;

        try {
            context.send(new ActionMessage(command, context.getNickname(), 0));
        } catch (IOException e) {
            appendLog("Failed to send: " + e.getMessage());
        }
    }

    /** Leaves the current room view and restores the gateway menu. */
    private void returnToGatewayMenuFromRoom() {
        sendAction("unready");
        prefillReconnectForm();
        disconnectSilently();
        resetPreStartControls();
        hideInGameMenu();
        switchScene(mainMenuScene);
        performNetworkConnection();
    }

    /**
     * Appends a line to the GUI log area.
     *
     * @param msg message to append.
     */
    private void appendLog(String msg) {
        if (msg == null) return;
        String visibleMessage = stripImageMetadata(msg);
        Platform.runLater(() -> {
            logArea.appendText(visibleMessage + "\n");
        });
    }

    /**
     * Removes card image metadata that is intended for GUI rendering rather than user-facing logs.
     *
     * @param text text that may contain image metadata
     * @return text without image metadata tags
     */
    private String stripImageMetadata(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return IMG_METADATA_PATTERN.matcher(text)
                .replaceAll("")
                .replaceAll("[ \\t]+(?=\\R|$)", "");
    }

    /**
     * Processes an incoming server update and refreshes relevant UI sections.
     *
     * @param update received update payload.
     */
    private void handleIncomingUpdate(SerializedUpdate update) {
        String type = update.getType();
        String content = update.getContent();

        if (type == null || content == null) return;

        if ("NOTIFICATION".equalsIgnoreCase(type)) {
            appendLog(content);
            maybeAppendPickedCardToHand(content);
            
            // Filter notifications to prevent spamming the central toast with routine actions.
            // Only show the toast for major events or bonuses.
            String lowerContent = content.toLowerCase();
            if (content.contains("🔥") ||
                content.contains("🍖") ||
                lowerContent.contains("bonus") ||
                lowerContent.contains("reward") ||
                lowerContent.contains("event calculations") ||
                lowerContent.contains("game over") ||
                lowerContent.contains("final scores") ||
                lowerContent.contains("winner") ||
                lowerContent.contains("winners")) {
                showThemedNotification(content);
            }
            
        } else if ("STATUS_UPDATE".equalsIgnoreCase(type)) {
            if (!isCompleteControllerStatus(content)) {
                return;
            }
            updateTopHud(content);
            updatePreStartReadyState(content);
            if (content.contains("initialized=true")) {
                showGameplayLayout();
            }

            updatePlayersStatus(content);
            currentToPlaceNickname = extractCurrentToPlace(content);
            parseAndRenderOfferTiles(content);
            updatePlacedTotems(content);
            updateOfferTilesInteractivity();

            updateActionResolutionState(content);
            refreshTurnIndicator();

            EnumMap<CardSection, List<CardDisplay>> parsed = parseStatusCardSections(content);
            renderCardRow(upperTribeBox, parsed.get(CardSection.UPPER_TRIBE), "upper", "t");
            renderCardRow(upperBuildingBox, parsed.get(CardSection.UPPER_BUILDINGS), "upper", "b");
            renderCardRow(lowerTribeBox, parsed.get(CardSection.LOWER_TRIBE), "lower", "t");
            renderCardRow(lowerBuildingBox, parsed.get(CardSection.LOWER_BUILDINGS), "lower", "b");
            
        } else if ("LOGIN_RESULT".equalsIgnoreCase(type)) {
            appendLog(content);
            if (update.isSuccess()) {
                resetPreStartControls();
                ConnectionContext context = activeConnection.get();
                if (context != null) {
                    context.startHeartbeat();
                }
                switchScene(gameScene, true);
            } else {
                showAlert(content);
            }
        } else if ("ERROR".equalsIgnoreCase(type)) {
            appendLog(content);
            showAlert(content);
        }
    }

    /**
     * Displays a transient, themed visual notification in the center of the screen.
     * Messages are queued and played sequentially to prevent overlapping.
     *
     * @param message notification text.
     */
    private void showThemedNotification(String message) {
        Platform.runLater(() -> {
            notificationQueue.offer(message);
            if (!isNotificationPlaying) {
                playNextNotification();
            }
        });
    }

    /** Displays the next queued notification when no notification is active. */
    private void playNextNotification() {
        String message = notificationQueue.poll();
        if (message == null) {
            isNotificationPlaying = false;
            return;
        }
        isNotificationPlaying = true;

        Platform.runLater(() -> {
            VBox toast = new VBox(15);
            toast.setAlignment(Pos.CENTER);
            toast.setMaxSize(780, 380);
            toast.setPadding(new Insets(30));
            // Semi-transparent background (alpha 0.68)
            toast.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(58,27,8,0.68), rgba(30,12,4,0.72)); " +
                          "-fx-background-radius: 15; -fx-border-color: #ecc94b; -fx-border-width: 3; -fx-border-radius: 15; " +
                          "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.9), 30, 0.5, 0, 10);");

            Text text = new Text(message.toUpperCase());
            boolean multiline = message.contains("\n");
            text.setFont(Font.font(multiline ? LABEL_FONT : MENU_FONT, FontWeight.BOLD, multiline ? 16 : 26));
            text.setFill(Color.web("#ffd66f"));
            text.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 4, 0.5, 0, 2);");
            text.setWrappingWidth(multiline ? 720 : 540);
            text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            toast.getChildren().add(text);
            boolean requiresNext = requiresNotificationAcknowledgement(message);
            if (requiresNext) {
                Button nextButton = new Button("Continue");
                styleGameActionButton(nextButton, true);
                nextButton.setMinWidth(140);
                nextButton.setMinHeight(42);
                toast.getChildren().add(nextButton);
            }
            toast.setOpacity(0);
            
            StackPane root = (StackPane) gameScene.getRoot();
            root.getChildren().add(toast);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            if (requiresNext) {
                Button nextButton = (Button) toast.getChildren().get(1);
                nextButton.setOnAction(e -> {
                    root.getChildren().remove(toast);
                    playNextNotification();
                });
                fadeIn.play();
                return;
            }

            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            FadeTransition fadeOut = new FadeTransition(Duration.millis(600), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                root.getChildren().remove(toast);
                playNextNotification(); // Trigger next in queue
            });

            new SequentialTransition(fadeIn, pause, fadeOut).play();
        });
    }

    /**
     * Determines whether a notification must remain visible until acknowledged.
     *
     * @param message notification text
     * @return {@code true} when explicit acknowledgement is required
     */
    private boolean requiresNotificationAcknowledgement(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return message.contains("🔥")
                || message.contains("🍖")
                || lower.contains("event calculations")
                || lower.contains("game over")
                || lower.contains("final scores")
                || lower.contains("winner");
    }

    /** Builds the modal menu displayed over an active game. */
    private void buildInGameMenuOverlay() {
        inGameMenuOverlay = new StackPane();
        inGameMenuOverlay.setVisible(false);
        inGameMenuOverlay.setManaged(false);
        // Semi-transparent deep tint (alpha 0.72) to let the game board show through
        inGameMenuOverlay.setStyle("-fx-background-color: rgba(12,6,3,0.72);");

        VBox menuBox = new VBox(25);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.setMaxWidth(450);
        menuBox.setMaxHeight(500); // Constraint height so it doesn't look too stretched
        menuBox.setPadding(new Insets(50, 60, 50, 60));
        menuBox.setStyle("-fx-background-color: rgba(42,20,9,0.96); -fx-background-radius: 15; -fx-border-color: #a66a2b; -fx-border-width: 3; -fx-border-radius: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 25, 0, 0, 12);");

        Text title = new Text("GAME MENU");
        title.setFont(Font.font(TITLE_FONT, 52));
        title.setFill(Color.web("#ffd66f"));
        title.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 4, 0.5, 0, 2);");

        Button resumeBtn = createMenuButton("RESUME", true);
        resumeBtn.setMinWidth(320);
        resumeBtn.setMinHeight(56);
        resumeBtn.setFont(Font.font(MENU_FONT, FontWeight.BOLD, 20));
        resumeBtn.setOnAction(e -> hideInGameMenu());

        Button lobbyBtn = createMenuButton("BACK TO LOBBY", false);
        lobbyBtn.setMinWidth(320);
        lobbyBtn.setMinHeight(56);
        lobbyBtn.setFont(Font.font(MENU_FONT, FontWeight.BOLD, 20));
        lobbyBtn.setOnAction(e -> {
            prefillReconnectForm();
            disconnectSilently();
            hideInGameMenu();
            switchScene(mainMenuScene);
            // Seamlessly re-establish a clean connection for the next room
            performNetworkConnection();
        });

        Button exitAppBtn = createMenuButton("EXIT GAME", false);
        exitAppBtn.setMinWidth(320);
        exitAppBtn.setMinHeight(56);
        exitAppBtn.setFont(Font.font(MENU_FONT, FontWeight.BOLD, 20));
        exitAppBtn.setOnAction(e -> Platform.exit());

        menuBox.getChildren().addAll(title, resumeBtn, lobbyBtn, exitAppBtn);
        inGameMenuOverlay.getChildren().add(menuBox);
    }

    /** Shows the in-game menu overlay. */
    private void showInGameMenu() {
        if (inGameMenuOverlay != null) {
            inGameMenuOverlay.setVisible(true);
            inGameMenuOverlay.setManaged(true);
            inGameMenuOverlay.toFront();
        }
    }

    /** Hides the in-game menu overlay. */
    private void hideInGameMenu() {
        if (inGameMenuOverlay != null) {
            inGameMenuOverlay.setVisible(false);
            inGameMenuOverlay.setManaged(false);
        }
    }

    /**
     * Updates top-level game indicators from a status payload.
     *
     * @param status latest server status
     */
    private void updateTopHud(String status) {
        ConnectionContext context = activeConnection.get();
        String nickname = context == null ? "-" : context.getNickname();
        if (connectedPlayerLabel != null) {
            String color = extractPlayerColor(status, nickname);
            String label = nickname == null || nickname.isBlank() ? "-" : nickname;
            if (color != null && !color.isBlank()) {
                label += " (" + color + ")";
            }
            connectedPlayerLabel.setText("Player: " + label);
            updateConnectedPlayerToken(color);
        }

        if (roundLabel != null) {
            Matcher matcher = ROUND_PATTERN.matcher(status);
            roundLabel.setText(matcher.find() ? "Round: " + matcher.group(1) : "Round: -");
        }
        if (eraLabel != null) {
            Matcher matcher = ERA_PATTERN.matcher(status);
            eraLabel.setText(matcher.find() ? "Era: " + matcher.group(1) : "Era: -");
        }

        updateTurnOrderTrackImage(status);
    }

    /**
     * Updates the turn-order track image and token overlays from a status payload.
     *
     * @param status raw status text from the server.
     */
    private void updateTurnOrderTrackImage(String status) {
        if (turnOrderTrackPane == null || turnOrderTrackView == null || status == null) {
            return;
        }

        Matcher matcher = PLAYERS_COUNT_PATTERN.matcher(status);
        if (!matcher.find()) {
            turnOrderTrackView.setImage(null);
            if (turnOrderTokenLayer != null) {
                turnOrderTokenLayer.getChildren().clear();
            }
            turnOrderTrackPane.setVisible(false);
            turnOrderTrackPane.setManaged(false);
            return;
        }

        int players = Integer.parseInt(matcher.group(1));
        Image image = players >= 2 && players <= 5
                ? ImageLoader.getTileImage(players + "p.png")
                : null;
        turnOrderTrackView.setImage(image);
        boolean visible = image != null;
        turnOrderTrackPane.setVisible(visible);
        turnOrderTrackPane.setManaged(visible);

        if (!visible) {
            if (turnOrderTokenLayer != null) {
                turnOrderTokenLayer.getChildren().clear();
            }
            return;
        }

        double width = turnOrderTrackView.getFitWidth();
        double height = image.getHeight() * width / image.getWidth();
        resizeTurnOrderTrack(width, height);
        renderTurnOrderTokens(status, players, width, height);
    }

    /**
     * On hover, shows only the turn-order tile without player tokens.
     */
    private void setTurnOrderTrackHover(boolean hovering) {
        if (turnOrderTokenLayer != null) {
            turnOrderTokenLayer.setVisible(!hovering);
        }
    }

    /**
     * Resizes and repositions the turn-order track for the current viewport.
     *
     * @param width available width
     * @param height available height
     */
    private void resizeTurnOrderTrack(double width, double height) {
        turnOrderTrackPane.setPrefSize(width, height);
        turnOrderTrackPane.setMinSize(width, height);
        turnOrderTrackPane.setMaxSize(width, height);
        if (turnOrderTokenLayer != null) {
            turnOrderTokenLayer.setPrefSize(width, height);
            turnOrderTokenLayer.setMinSize(width, height);
            turnOrderTokenLayer.setMaxSize(width, height);
        }
    }

    /**
     * Draws player tokens over the turn-order track according to the current order.
     *
     * @param status raw status text from the server.
     * @param players number of players in the match.
     * @param width rendered track width.
     * @param height rendered track height.
     */
    private void renderTurnOrderTokens(String status, int players, double width, double height) {
        if (turnOrderTokenLayer == null) {
            return;
        }
        turnOrderTokenLayer.getChildren().clear();

        List<String> orderedNames = extractTurnOrderNames(status);
        Map<String, String> colorsByNick = extractPlayerColors(status);
        double[][] slots = turnOrderSlots(players);
        int count = Math.min(Math.min(orderedNames.size(), slots.length), players);
        double tokenHeight = Math.max(26, width * 0.45);

        for (int i = 0; i < count; i++) {
            String color = colorsByNick.get(orderedNames.get(i).toLowerCase());
            Image tokenImage = ImageLoader.getTokenImage(color);
            if (tokenImage == null) {
                continue;
            }

            ImageView tokenView = new ImageView(tokenImage);
            tokenView.setFitHeight(tokenHeight);
            tokenView.setPreserveRatio(true);
            tokenView.setSmooth(true);

            double tokenWidth = tokenImage.getWidth() * tokenHeight / tokenImage.getHeight();
            tokenView.setLayoutX(width * slots[i][0] - tokenWidth / 2.0);
            tokenView.setLayoutY(height * slots[i][1] - tokenHeight / 2.0);
            turnOrderTokenLayer.getChildren().add(tokenView);
        }
    }

    /**
     * Returns normalized token positions for each turn-order track image.
     *
     * @param players number of players in the match.
     * @return normalized x/y coordinates for token centers.
     */
    private double[][] turnOrderSlots(int players) {
        switch (players) {
            case 2:
                return new double[][]{{0.50, 0.25}, {0.50, 0.46}};
            case 3:
                return new double[][]{{0.50, 0.26}, {0.50, 0.49}, {0.50, 0.68}};
            case 4:
                return new double[][]{{0.50, 0.18}, {0.50, 0.35}, {0.50, 0.55}, {0.50, 0.76}};
            case 5:
                return new double[][]{{0.50, 0.10}, {0.50, 0.28}, {0.50, 0.45}, {0.50, 0.63}, {0.50, 0.84}};
            default:
                return new double[0][0];
        }
    }

    /**
     * Extracts the currently displayed player order from placement/action sections.
     *
     * @param status raw status text from the server.
     * @return player nicknames in displayed order.
     */
    private List<String> extractTurnOrderNames(String status) {
        List<String> names = extractNamesFromOrderSection(status, "placement order:");
        if (names.isEmpty()) {
            names = extractNamesFromOrderSection(status, "action order:");
        }
        if (names.isEmpty()) {
            for (String row : parsePlayersLines(status)) {
                Matcher matcher = PLAYER_ROW_PATTERN.matcher(row);
                if (matcher.matches()) {
                    names.add(matcher.group(1).trim());
                }
            }
        }
        return names;
    }

    /**
     * Extracts player names from a specific order section in the status text.
     *
     * @param status raw status text from the server.
     * @param sectionHeader section header to parse.
     * @return names listed below the requested section header.
     */
    private List<String> extractNamesFromOrderSection(String status, String sectionHeader) {
        List<String> names = new ArrayList<>();
        boolean inSection = false;
        for (String line : status.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase(sectionHeader)) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (trimmed.isEmpty()) {
                break;
            }
            if (trimmed.contains(":")) {
                break;
            }

            String name = trimmed;
            if (name.startsWith("->")) {
                name = name.substring(2).trim();
            }
            int tileIndex = name.toLowerCase().indexOf(" tile=");
            if (tileIndex >= 0) {
                name = name.substring(0, tileIndex).trim();
            }
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Builds a nickname-to-color lookup from player rows in the status text.
     *
     * @param status raw status text from the server.
     * @return map keyed by lowercase nickname.
     */
    private Map<String, String> extractPlayerColors(String status) {
        Map<String, String> colorsByNick = new HashMap<>();
        for (String row : parsePlayersLines(status)) {
            Matcher matcher = PLAYER_ROW_PATTERN.matcher(row);
            if (matcher.matches() && matcher.group(2) != null) {
                colorsByNick.put(matcher.group(1).trim().toLowerCase(), matcher.group(2).trim());
            }
        }
        return colorsByNick;
    }

    /**
     * Updates the local player's token image and color.
     *
     * @param color server-provided color name
     */
    private void updateConnectedPlayerToken(String color) {
        if (connectedPlayerTokenView == null) {
            return;
        }
        Image tokenImage = ImageLoader.getTokenFrontImage(color);
        connectedPlayerTokenView.setImage(tokenImage);
        boolean visible = tokenImage != null;
        connectedPlayerTokenView.setVisible(visible);
        connectedPlayerTokenView.setManaged(visible);
    }

    /**
     * Extracts a player's color from a status payload.
     *
     * @param status server status text
     * @param nickname player to locate
     * @return the color name, or {@code null} when unavailable
     */
    private String extractPlayerColor(String status, String nickname) {
        if (status == null || nickname == null || nickname.isBlank()) {
            return null;
        }
        for (String row : parsePlayersLines(status)) {
            Matcher matcher = PLAYER_ROW_PATTERN.matcher(row);
            if (matcher.matches() && nickname.equalsIgnoreCase(matcher.group(1).trim())) {
                return matcher.group(2);
            }
        }
        return null;
    }

    /**
     * Updates the player status panel and local resource counters from a status payload.
     *
     * @param content raw status text.
     */
    private void updatePlayersStatus(String content) {
        List<String> players = parsePlayersLines(content);
        takenCardsByPlayer.clear();
        takenCardsByPlayer.putAll(parseTakenCardsByPlayer(content));
        playersBox.getChildren().clear();
        playersBox.getChildren().add(createSidePanelTitle("Players"));

        ConnectionContext ctx = activeConnection.get();
        String myNick = ctx != null ? ctx.getNickname() : "";
        synchronizeLocalHand(myNick);

        for (String pLine : players) {
            Matcher m = PLAYER_ROW_PATTERN.matcher(pLine);
            if (m.matches()) {
                String nick = m.group(1).trim();
                String food = m.group(3);
                String pp = m.group(4);

                if (nick.equalsIgnoreCase(myNick) || ("You: " + myNick).equalsIgnoreCase(pLine) || pLine.startsWith("You:")) {
                    foodValueLabel.setText("🍗 " + food);
                    prestigeValueLabel.setText("⭐ " + pp);
                }

                String playerColor = m.group(2);
                String labelText = nick + (playerColor == null ? "" : " (" + playerColor + ")") + "   🍗 " + food + "   ⭐ " + pp;
                Label l = new Label(labelText);
                l.setTextFill(Color.web(nick.equalsIgnoreCase(myNick) ? "#fff0c2" : "#f5d28a"));
                l.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
                l.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.45, 0, 1);");

                if (!nick.equalsIgnoreCase(myNick)) {
                    Button handBtn = new Button("Hand");
                    stylePlayerHandButton(handBtn);
                    handBtn.setOnAction(e -> showPlayerHandOverlay(nick));
                    handBtn.setVisible(false);
                    handBtn.setManaged(false);

                    l.setMinWidth(0);
                    l.setMaxWidth(Double.MAX_VALUE);
                    l.setTextOverrun(OverrunStyle.ELLIPSIS);
                    l.setTooltip(new Tooltip(labelText));
                    HBox.setHgrow(l, Priority.ALWAYS);

                    HBox row = new HBox(8, l, handBtn);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setOnMouseEntered(e -> {
                        handBtn.setVisible(true);
                        handBtn.setManaged(true);
                    });
                    row.setOnMouseExited(e -> {
                        handBtn.setVisible(false);
                        handBtn.setManaged(false);
                    });
                    playersBox.getChildren().add(row);
                } else {
                    playersBox.getChildren().add(l);
                }
            } else {
                Label l = new Label(pLine);
                l.setTextFill(Color.web("#f5d28a"));
                l.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
                l.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.45, 0, 1);");
                playersBox.getChildren().add(l);
            }
        }
    }

    /**
     * Returns whether a status payload is the complete snapshot produced by the game controller.
     * Compact model change events are ignored because they do not contain enough data to refresh the GUI.
     *
     * @param content status payload to inspect
     * @return {@code true} for a complete controller snapshot
     */
    private boolean isCompleteControllerStatus(String content) {
        return content != null
                && content.startsWith("Status:")
                && content.contains("initialized=")
                && content.contains("\nPhase:");
    }

    /**
     * Synchronizes the local hand with the authoritative card identifiers from the latest status snapshot.
     * Existing card descriptions are preserved when available.
     *
     * @param nickname local player's nickname
     */
    private void synchronizeLocalHand(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }

        List<Integer> authoritativeIds = null;
        for (Map.Entry<String, List<Integer>> entry : takenCardsByPlayer.entrySet()) {
            if (nickname.equalsIgnoreCase(entry.getKey())) {
                authoritativeIds = entry.getValue();
                break;
            }
        }
        if (authoritativeIds == null) {
            authoritativeIds = List.of();
        }

        Map<Integer, CardDisplay> existingCards = new HashMap<>();
        for (CardDisplay card : playerHandCards) {
            existingCards.put(card.assetId, card);
        }

        playerHandCards.clear();
        for (Integer assetId : authoritativeIds) {
            if (assetId == null) {
                continue;
            }
            CardDisplay existing = existingCards.get(assetId);
            playerHandCards.add(existing != null
                    ? existing
                    : new CardDisplay(assetId, "Owned card {img=" + String.format("%03d", assetId) + "}"));
        }
        renderPlayerHand();
    }

    /**
     * Applies the standard style to a player-hand button.
     *
     * @param button button to style
     */
    private void stylePlayerHandButton(Button button) {
        button.setMinHeight(24);
        button.setMinWidth(58);
        button.setMaxHeight(24);
        button.setFocusTraversable(false);
        button.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 10));
        button.setTextFill(Color.web("#fff0c2"));
        button.setStyle("-fx-background-color: linear-gradient(to bottom, #6a2d14, #2d1309); " +
                "-fx-background-radius: 6; -fx-padding: 2 10; " +
                "-fx-border-color: #120806; -fx-border-width: 1.5; -fx-border-radius: 6; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.65), 6, 0, 0, 2);");
    }

    /**
     * Opens the card overlay for a specific player.
     *
     * @param nickname player whose hand is displayed
     */
    private void showPlayerHandOverlay(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            showAlert("Invalid player.");
            return;
        }

        List<Integer> assetIds = takenCardsByPlayer.get(nickname);
        if (assetIds == null) {
            assetIds = new ArrayList<>();
        }

        if (playerHandOverlay == null) {
            return;
        }

        playerHandOverlay.getChildren().clear();

        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(14, 22, 16, 22));
        panel.setPrefHeight(270);
        panel.setMinHeight(235);
        panel.setMaxHeight(300);
        panel.setMaxWidth(760);
        panel.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(31,14,6,0.95), rgba(5,3,2,0.98)); " +
                "-fx-background-radius: 7; -fx-border-color: #8a5118; -fx-border-width: 2.4; " +
                "-fx-border-radius: 7; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.82), 18, 0.2, 0, 8);");
        panel.setOnMouseClicked(e -> e.consume());

        Label title = createGameSectionTitle(nickname.toUpperCase() + "'S HAND");
        title.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 21));
        title.setTextFill(Color.web("#ffd66f"));

        Button closeButton = new Button("X");
        closeButton.setFocusTraversable(false);
        closeButton.setMinSize(30, 26);
        closeButton.setMaxSize(30, 26);
        closeButton.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 12));
        closeButton.setTextFill(Color.web("#fff0c2"));
        closeButton.setStyle("-fx-background-color: rgba(82,34,14,0.95); -fx-background-radius: 5; " +
                "-fx-border-color: #130704; -fx-border-width: 1.2; -fx-border-radius: 5; -fx-padding: 0;");
        closeButton.setOnAction(e -> hidePlayerHandOverlay());

        StackPane titleBar = new StackPane(title, closeButton);
        titleBar.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(title, Pos.CENTER);
        StackPane.setAlignment(closeButton, Pos.CENTER_RIGHT);
        panel.getChildren().add(titleBar);

        if (assetIds.isEmpty()) {
            Label empty = new Label("No cards");
            empty.setTextFill(Color.web("#f5d28a"));
            empty.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
            empty.setMinHeight(150);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.45, 0, 1);");
            panel.getChildren().add(empty);
        } else {
            HBox cardsBox = new HBox(24);
            cardsBox.setAlignment(Pos.CENTER);
            cardsBox.setPadding(new Insets(4, 18, 10, 18));
            cardsBox.setMinWidth(650);
            for (Integer id : assetIds) {
                if (id == null || id <= 0) continue;
                cardsBox.getChildren().add(new CardNode(id, "{img=" + String.format("%03d", id) + "}", null));
            }

            ScrollPane scrollPane = new ScrollPane(cardsBox);
            scrollPane.getStyleClass().add("invisible-scroll");
            scrollPane.setPannable(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setFitToWidth(false);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            scrollPane.setBackground(Background.EMPTY);
            scrollPane.setBorder(Border.EMPTY);
            scrollPane.setPrefViewportHeight(165);
            scrollPane.setPrefViewportWidth(690);
            panel.getChildren().add(scrollPane);
        }

        playerHandOverlay.getChildren().add(panel);
        playerHandOverlay.toFront();
        playerHandOverlay.setManaged(true);
        playerHandOverlay.setVisible(true);
    }

    /**
     * Shows the local player's hand in a square overlay with a vertically scrollable grid.
     */
    private void showOwnHandOverlay() {
        if (playerHandOverlay == null) {
            return;
        }

        playerHandOverlay.getChildren().clear();

        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(16, 18, 18, 18));
        panel.setPrefSize(560, 560);
        panel.setMinSize(560, 560);
        panel.setMaxSize(560, 560);
        panel.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(31,14,6,0.96), rgba(5,3,2,0.98)); " +
                "-fx-background-radius: 7; -fx-border-color: #8a5118; -fx-border-width: 2.4; " +
                "-fx-border-radius: 7; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.82), 18, 0.2, 0, 8);");
        panel.setOnMouseClicked(e -> e.consume());

        Label title = createGameSectionTitle("YOUR CARDS");
        title.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#ffd66f"));

        Button closeButton = new Button("X");
        closeButton.setFocusTraversable(false);
        closeButton.setMinSize(30, 26);
        closeButton.setMaxSize(30, 26);
        closeButton.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 12));
        closeButton.setTextFill(Color.web("#fff0c2"));
        closeButton.setStyle("-fx-background-color: rgba(82,34,14,0.95); -fx-background-radius: 5; " +
                "-fx-border-color: #130704; -fx-border-width: 1.2; -fx-border-radius: 5; -fx-padding: 0;");
        closeButton.setOnAction(e -> hidePlayerHandOverlay());

        StackPane titleBar = new StackPane(title, closeButton);
        titleBar.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(title, Pos.CENTER);
        StackPane.setAlignment(closeButton, Pos.CENTER_RIGHT);
        panel.getChildren().add(titleBar);

        if (playerHandCards.isEmpty()) {
            Label empty = new Label("No cards");
            empty.setTextFill(Color.web("#f5d28a"));
            empty.setFont(Font.font(LABEL_FONT, FontWeight.BOLD, 15));
            empty.setMinHeight(470);
            empty.setAlignment(Pos.CENTER);
            panel.getChildren().add(empty);
        } else {
            TilePane cardsGrid = new TilePane();
            cardsGrid.setAlignment(Pos.TOP_CENTER);
            cardsGrid.setPadding(new Insets(8, 10, 12, 10));
            cardsGrid.setHgap(16);
            cardsGrid.setVgap(16);
            cardsGrid.setPrefColumns(4);
            cardsGrid.setPrefTileWidth(100);
            cardsGrid.setPrefTileHeight(150);

            for (CardDisplay card : playerHandCards) {
                cardsGrid.getChildren().add(new CardNode(card.assetId, card.tooltip, null, 92));
            }

            ScrollPane scrollPane = new ScrollPane(cardsGrid);
            scrollPane.setPannable(true);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            scrollPane.setBackground(Background.EMPTY);
            scrollPane.setBorder(Border.EMPTY);
            scrollPane.setPrefViewportWidth(515);
            scrollPane.setPrefViewportHeight(478);
            scrollPane.setMaxHeight(478);
            panel.getChildren().add(scrollPane);
        }

        playerHandOverlay.getChildren().add(panel);
        playerHandOverlay.toFront();
        playerHandOverlay.setManaged(true);
        playerHandOverlay.setVisible(true);
    }

    /** Hides the player-hand overlay and clears its transient state. */
    private void hidePlayerHandOverlay() {
        if (playerHandOverlay == null) {
            return;
        }
        playerHandOverlay.setVisible(false);
        playerHandOverlay.setManaged(false);
        playerHandOverlay.getChildren().clear();
    }

    /**
     * Parses taken-card asset identifiers grouped by player from a status payload.
     *
     * @param content server status text
     * @return card identifiers keyed by nickname
     */
    private Map<String, List<Integer>> parseTakenCardsByPlayer(String content) {
        Map<String, List<Integer>> result = new HashMap<>();
        if (content == null) {
            return result;
        }

        boolean inSection = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (!inSection) {
                if ("Taken cards:".equalsIgnoreCase(line)) {
                    inSection = true;
                }
                continue;
            }

            if (line.isEmpty()) {
                break;
            }

            int idx = line.toLowerCase().indexOf(" cards=");
            if (idx <= 0) {
                continue;
            }

            String nick = line.substring(0, idx).trim();
            List<Integer> ids = extractAllAssetIds(line);
            result.put(nick, ids);
        }

        return result;
    }

    /**
     * Extracts every card asset identifier embedded in text.
     *
     * @param text text to scan
     * @return identifiers in encounter order
     */
    private List<Integer> extractAllAssetIds(String text) {
        List<Integer> ids = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return ids;
        }

        Matcher matcher = IMG_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                ids.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // ignore invalid img tag
            }
        }

        return ids;
    }

    /** Updates the pre-start ready controls from lobby status text. */
    private void updatePreStartReadyState(String content) {
        if (readyButton == null || removeReadyButton == null || readyStatusLabel == null) {
            return;
        }
        if (content == null || content.contains("initialized=true")) {
            readyButton.setVisible(false);
            readyButton.setManaged(false);
            removeReadyButton.setVisible(false);
            removeReadyButton.setManaged(false);
            removeReadyButton.setDisable(false);
            if (gatewayLobbyButton != null) {
                gatewayLobbyButton.setVisible(false);
                gatewayLobbyButton.setManaged(false);
            }
            hideColorSelectionControls();
            return;
        }
        if (gatewayLobbyButton != null) {
            gatewayLobbyButton.setVisible(true);
            gatewayLobbyButton.setManaged(true);
            gatewayLobbyButton.setDisable(false);
        }

        String readyPlayers = "";
        String readyCount = "0";
        String colorSelectionTurn = "";
        String availableColors = "";

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("ready players:")) {
                readyPlayers = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            } else if (lower.startsWith("ready count:")) {
                readyCount = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            } else if (lower.startsWith("color selection turn:")) {
                colorSelectionTurn = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            } else if (lower.startsWith("available colors:")) {
                availableColors = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            }
        }

        readyStatusLabel.setText("Ready players: " + readyCount);

        ConnectionContext ctx = activeConnection.get();
        String myNick = ctx == null ? null : ctx.getNickname();
        boolean iAmReady = myNick != null && nameListContains(readyPlayers, myNick);
        boolean choosingColors = colorSelectionTurn != null && !colorSelectionTurn.isBlank() && !"(none)".equals(colorSelectionTurn);

        if (choosingColors) {
            if (startPromptLabel != null) {
                startPromptLabel.setText("CHOOSE PLAYER COLORS");
            }
            readyButton.setVisible(false);
            readyButton.setManaged(false);
            removeReadyButton.setVisible(false);
            removeReadyButton.setManaged(false);
            removeReadyButton.setDisable(false);
            renderColorSelection(colorSelectionTurn, availableColors);
            return;
        }

        hideColorSelectionControls();
        if (startPromptLabel != null) {
            startPromptLabel.setText("WAITING FOR PLAYERS");
        }

        readyButton.setVisible(!iAmReady);
        readyButton.setManaged(!iAmReady);
        readyButton.setDisable(false);

        removeReadyButton.setVisible(iAmReady);
        removeReadyButton.setManaged(iAmReady);
        removeReadyButton.setDisable(false);
    }

    /**
     * Resets the lobby controls to their safe initial state before receiving room status.
     */
    private void resetPreStartControls() {
        if (readyButton != null) {
            readyButton.setVisible(true);
            readyButton.setManaged(true);
            readyButton.setDisable(false);
        }
        if (removeReadyButton != null) {
            removeReadyButton.setVisible(false);
            removeReadyButton.setManaged(false);
            removeReadyButton.setDisable(false);
        }
        if (gatewayLobbyButton != null) {
            gatewayLobbyButton.setVisible(true);
            gatewayLobbyButton.setManaged(true);
            gatewayLobbyButton.setDisable(false);
        }
        if (readyStatusLabel != null) {
            readyStatusLabel.setText("Ready players: 0");
        }
        playerHandCards.clear();
        if (handBox != null) {
            handBox.getChildren().clear();
        }
        if (startPromptLabel != null) {
            startPromptLabel.setText("WAITING FOR PLAYERS");
        }
        hideColorSelectionControls();
    }

    /**
     * Renders color-selection controls for the current lobby state.
     *
     * @param currentChooser nickname currently choosing
     * @param availableColors comma-separated available colors
     */
    private void renderColorSelection(String currentChooser, String availableColors) {
        if (colorSelectionBox == null || colorSelectionLabel == null) {
            return;
        }

        ConnectionContext ctx = activeConnection.get();
        String myNick = ctx == null ? null : ctx.getNickname();
        boolean myTurn = myNick != null && myNick.equalsIgnoreCase(currentChooser);

        colorSelectionLabel.setText(myTurn ? "Choose your color" : "Waiting for " + currentChooser + " to choose color");
        colorSelectionLabel.setVisible(true);
        colorSelectionLabel.setManaged(true);

        colorSelectionBox.getChildren().clear();
        for (String colorName : splitCsv(availableColors)) {
            colorSelectionBox.getChildren().add(createColorTokenButton(colorName, myTurn));
        }
        colorSelectionBox.setVisible(true);
        colorSelectionBox.setManaged(true);
    }

    /** Hides and clears color-selection controls. */
    private void hideColorSelectionControls() {
        if (colorSelectionLabel != null) {
            colorSelectionLabel.setVisible(false);
            colorSelectionLabel.setManaged(false);
            colorSelectionLabel.setText("");
        }
        if (colorSelectionBox != null) {
            colorSelectionBox.setVisible(false);
            colorSelectionBox.setManaged(false);
            colorSelectionBox.getChildren().clear();
        }
    }

    /**
     * Splits and trims a comma-separated value list.
     *
     * @param csv value list
     * @return non-empty trimmed values
     */
    private List<String> splitCsv(String csv) {
        List<String> values = new ArrayList<>();
        if (csv == null || csv.isBlank() || "(none)".equals(csv)) {
            return values;
        }
        for (String item : csv.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Tests whether a comma-separated name list contains an exact name.
     *
     * @param csv name list
     * @param name name to find
     * @return {@code true} when the name is present
     */
    private boolean nameListContains(String csv, String name) {
        if (csv == null || name == null || csv.equals("(none)")) {
            return false;
        }
        for (String item : csv.split(",")) {
            if (name.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Updates the left-side current turn indicator. */
    private void refreshTurnIndicator() {
        if (turnOwnerLabel == null) {
            return;
        }

        String nick = currentActingNickname;
        if (nick == null || nick.isBlank()) {
            nick = currentToPlaceNickname;
        }
        turnOwnerLabel.setText(nick == null || nick.isBlank() ? "Waiting" : nick);

        if (turnInstructionLabel == null) {
            return;
        }
        if (currentActingNickname != null && !currentActingNickname.isBlank()) {
            turnInstructionLabel.setText("Take " + remainingUpperPicks + " upper row\nand " + remainingLowerPicks + " lower row");
        } else if (currentToPlaceNickname != null && !currentToPlaceNickname.isBlank()) {
            turnInstructionLabel.setText("Choose an offer tile");
        } else {
            turnInstructionLabel.setText("Waiting");
        }
    }

    /**
     * Parses and renders available offer tiles from a status payload.
     *
     * @param content raw status text.
     */
    private void parseAndRenderOfferTiles(String content) {
        boolean rendered = false;
        placedTotemColorsByTile.clear();
        placedTotemColorsByTile.putAll(parsePlacedTotemColorsByTile(content));
        for (String line : content.split("\\R")) {
            line = line.trim();
            String tilesData = extractOfferTilesData(line);
            if (tilesData != null) {
                offerTilesBox.getChildren().clear();
                offerTileButtons.clear();
                if (tilesData.equals("(none)") || tilesData.isEmpty()) {
                    showOfferTilesPlaceholder("No offer tiles available");
                    updateOfferTilesInteractivity();
                    return;
                }

                String[] tiles = tilesData.split(",");
                for (String tileInfo : tiles) {
                    tileInfo = tileInfo.trim();
                    if (tileInfo.isEmpty()) continue;
                    
                    String letter = tileInfo.substring(0, 1);
                    String normalizedLetter = letter.toUpperCase();
                    Button tileBtn = createOfferTileButton(letter, tileInfo, placedTotemColorsByTile.get(normalizedLetter));
                    tileBtn.setOnAction(e -> {
                        sendAction("totem " + letter);
                        disableAllOfferTileButtons();
                    });

                    offerTileButtons.put(normalizedLetter, tileBtn);
                    offerTilesBox.getChildren().add(tileBtn);
                }
                rendered = true;
                updateOfferTilesInteractivity();
                break;
            }
        }

        if (!rendered && offerTilesBox.getChildren().isEmpty()) {
            showOfferTilesPlaceholder("Waiting for offer tiles...");
            updateOfferTilesInteractivity();
        }
    }

    /**
     * Extracts offer-tile data from a status line.
     *
     * @param line status line to inspect.
     * @return tile data after the separator, or {@code null} when absent.
     */
    private String extractOfferTilesData(String line) {
        String lower = line.toLowerCase();
        if (lower.startsWith("available tiles:")) {
            return line.substring(line.indexOf(':') + 1).trim();
        }
        if (lower.startsWith("offer tiles:")) {
            return line.substring(line.indexOf(':') + 1).trim();
        }
        return null;
    }

    /**
     * Maps occupied offer tiles to the color of the player who placed the totem.
     *
     * @param content raw status payload.
     * @return tile letter to player color map.
     */
    private Map<String, String> parsePlacedTotemColorsByTile(String content) {
        Map<String, String> colorsByTile = new HashMap<>();
        for (String row : parsePlayersLines(content)) {
            Matcher m = PLAYER_ROW_PATTERN.matcher(row);
            if (!m.matches()) {
                continue;
            }

            String color = m.group(2);
            String tile = m.group(5);
            if (color == null || tile == null || "-".equals(tile.trim())) {
                continue;
            }

            colorsByTile.put(tile.trim().toUpperCase(), color.trim().toUpperCase());
        }
        return colorsByTile;
    }

    /**
     * Shows a placeholder message in the offer-tile area.
     *
     * @param text placeholder text to display.
     */
    private void showOfferTilesPlaceholder(String text) {
        offerTilesBox.getChildren().clear();
        Label placeholder = new Label(text);
        placeholder.setWrapText(true);
        placeholder.setTextFill(Color.web("#a0aec0"));
        placeholder.setFont(Font.font("System", FontWeight.BOLD, 14));
        placeholder.setMaxWidth(180);
        offerTilesBox.getChildren().add(placeholder);
    }

    /** Disables all currently rendered offer-tile buttons after a placement action. */
    private void disableAllOfferTileButtons() {
        for (Button button : offerTileButtons.values()) {
            button.setDisable(true);
            button.setOpacity(0.55);
        }
    }

    /** Enables offer tiles only when the local player is the one expected to place a totem. */
    private void updateOfferTilesInteractivity() {
        ConnectionContext ctx = activeConnection.get();
        String myNick = ctx != null ? ctx.getNickname() : null;
        boolean myTurn = myNick != null
                && currentToPlaceNickname != null
                && myNick.equalsIgnoreCase(currentToPlaceNickname);

        for (Map.Entry<String, Button> entry : offerTileButtons.entrySet()) {
            String letter = entry.getKey();
            Button button = entry.getValue();
            boolean occupied = placedTotemColorsByTile.containsKey(letter);
            button.setDisable(occupied || !myTurn);
            button.setOpacity(occupied || myTurn ? 1.0 : 0.55);
            if (occupied) {
                Tooltip.install(button, new Tooltip("Already selected"));
            } else if (!myTurn && currentToPlaceNickname != null) {
                Tooltip.install(button, new Tooltip("Waiting for: " + currentToPlaceNickname));
            }
        }
    }

    /**
     * Extracts the nickname of the player currently expected to place a totem.
     *
     * @param content raw status payload.
     * @return nickname to place next, or {@code null} if unavailable.
     */
    private String extractCurrentToPlace(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("to place:")) {
                int idx = trimmed.indexOf(':');
                if (idx >= 0 && idx + 1 < trimmed.length()) {
                    return trimmed.substring(idx + 1).trim();
                }
            }
        }
        return null;
    }

    /**
     * Refreshes the placed-totems panel from player rows in the status payload.
     *
     * @param content raw status payload.
     */
    private void updatePlacedTotems(String content) {
        if (placedTotemsBox == null) {
            return;
        }

        List<String> rows = parsePlayersLines(content);
        placedTotemsBox.getChildren().clear();

        boolean anyPlacement = false;
        for (String row : rows) {
            Matcher m = PLAYER_ROW_PATTERN.matcher(row);
            if (!m.matches()) {
                continue;
            }

            String nick = m.group(1).trim();
            String tile = m.group(5).trim();
            if ("-".equals(tile)) {
                continue;
            }

            anyPlacement = true;
            Label placement = new Label(nick + " -> " + tile.toUpperCase());
            placement.setTextFill(Color.WHITE);
            placement.setFont(Font.font("System", FontWeight.BOLD, 13));
            placedTotemsBox.getChildren().add(placement);
        }

        if (!anyPlacement) {
            showPlacedTotemsPlaceholder();
        }
    }

    /** Shows the default empty-state message for the placed-totems panel. */
    private void showPlacedTotemsPlaceholder() {
        if (placedTotemsBox == null) {
            return;
        }

        placedTotemsBox.getChildren().clear();
        Label placeholder = new Label("No totems placed yet");
        placeholder.setTextFill(Color.web("#a0aec0"));
        placeholder.setFont(Font.font("System", 13));
        placeholder.setWrapText(true);
        placedTotemsBox.getChildren().add(placeholder);
    }

    /**
     * Renders a card row into the target box and wires the pick action for each card.
     *
     * @param box target UI row.
     * @param cards cards to display.
     * @param row protocol row token.
     * @param kind protocol kind token.
     */
    private void renderCardRow(HBox box, List<CardDisplay> cards, String row, String kind) {
        box.getChildren().clear();
        if (cards == null) return;

        boolean canPickFromRow = canPickFromRow(row);

        for (int i = 0; i < cards.size(); i++) {
            CardDisplay cd = cards.get(i);
            final int index = i;

            boolean isEventCard = "t".equalsIgnoreCase(kind)
                    && cd.tooltip != null
                    && cd.tooltip.trim().toUpperCase().startsWith("EVENT");

            Runnable onClick = null;
            if (canPickFromRow && !isEventCard) {
                onClick = () -> sendAction("pick " + row + " " + kind + " " + index);
            }

            CardNode node = new CardNode(cd.assetId, cd.tooltip, onClick);
            if (onClick == null) {
                node.setOpacity(isEventCard ? 0.4 : 0.65);
            }
            box.getChildren().add(node);
        }
    }

    /**
     * Checks whether the local player can currently pick a card from the given row.
     *
     * @param row row identifier, typically {@code upper} or {@code lower}.
     * @return {@code true} if the active player has remaining picks in that row.
     */
    private boolean canPickFromRow(String row) {
        ConnectionContext ctx = activeConnection.get();
        String myNick = ctx != null ? ctx.getNickname() : null;
        if (myNick == null || currentActingNickname == null) {
            return false;
        }
        if (!myNick.equalsIgnoreCase(currentActingNickname)) {
            return false;
        }

        if ("upper".equalsIgnoreCase(row)) {
            return remainingUpperPicks > 0;
        }
        if ("lower".equalsIgnoreCase(row)) {
            return remainingLowerPicks > 0;
        }
        return false;
    }

    /**
     * Updates the active card-picking state from the server status payload.
     *
     * @param content raw status payload.
     */
    private void updateActionResolutionState(String content) {
        String oldActing = currentActingNickname;
        int oldUpper = remainingUpperPicks;
        int oldLower = remainingLowerPicks;

        currentActingNickname = null;
        remainingUpperPicks = 0;
        remainingLowerPicks = 0;

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            Matcher matcher = ACTING_ROW_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                currentActingNickname = matcher.group(1).trim();
                try {
                    remainingUpperPicks = Integer.parseInt(matcher.group(2));
                    remainingLowerPicks = Integer.parseInt(matcher.group(3));
                } catch (NumberFormatException ignored) {
                    remainingUpperPicks = 0;
                    remainingLowerPicks = 0;
                }
                
                // Debug log for multi-pick verification
                if (currentActingNickname.equals(oldActing) && (remainingUpperPicks > oldUpper || remainingLowerPicks > oldLower)) {
                    System.out.println("[GUI] Extra picks detected for " + currentActingNickname);
                }
                return;
            }
        }
    }

    /**
     * If a "Taken:" notification is received, appends the picked card to the hand panel.
     *
     * @param message server notification text.
     */
    private void maybeAppendPickedCardToHand(String message) {
        if (message == null) return;
        
        for (String line : message.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "Taken:", 0, 6)) {
                Integer assetId = extractAssetId(trimmed);
                if (assetId != null) {
                    playerHandCards.add(new CardDisplay(assetId, trimmed));
                    renderPlayerHand();
                }
            }
            
            // Resource Gain Feedback (from new game logic)
            if (trimmed.contains("gained") || trimmed.contains("bonus") || trimmed.contains("reward")) {
                appendLog(trimmed);
            }
        }
    }

    /** Rebuilds the local player's sorted hand view. */
    private void renderPlayerHand() {
        if (handBox == null) {
            return;
        }
        playerHandCards.sort(Comparator
                .comparing((CardDisplay card) -> cardTypeSortKey(card.tooltip))
                .thenComparingInt(card -> card.assetId));
        handBox.getChildren().clear();
        handBox.setTranslateX(0);
        for (CardDisplay card : playerHandCards) {
            handBox.getChildren().add(new CardNode(card.assetId, card.tooltip, null, 76));
        }
        updateHandExpandButton();
    }

    /**
     * Shows the hand expansion button only when the preview row cannot fit all cards.
     */
    private void updateHandExpandButton() {
        if (handExpandButton == null) {
            return;
        }
        double cardWidth = 76;
        double gap = 10;
        double previewWidth = 620;
        double contentWidth = playerHandCards.isEmpty()
                ? 0
                : playerHandCards.size() * cardWidth + (playerHandCards.size() - 1) * gap;
        boolean overflowing = contentWidth > previewWidth;
        handExpandButton.setVisible(overflowing);
        handExpandButton.setManaged(overflowing);
    }

    /**
     * Derives a stable card-type key used to sort hand entries.
     *
     * @param tooltip card tooltip text
     * @return the card-type sort key
     */
    private String cardTypeSortKey(String tooltip) {
        String text = tooltip == null ? "" : tooltip.toUpperCase();
        if (text.contains("HUNTER") || text.contains("CACCIAT")) return "10_HUNTER";
        if (text.contains("GATHERER") || text.contains("RACCOGL")) return "20_GATHERER";
        if (text.contains("BUILDER") || text.contains("COSTRUT")) return "30_BUILDER";
        if (text.contains("SHAMAN") || text.contains("SCIAMAN")) return "40_SHAMAN";
        if (text.contains("ARTIST") || text.contains("ARTISTA")) return "50_ARTIST";
        if (text.contains("INVENTOR") || text.contains("INVENT")) return "60_INVENTOR";
        if (text.contains("BUILDING") || text.contains("BUILDINGS")) return "70_BUILDING";
        if (text.contains("EVENT")) return "80_EVENT";
        return "90_" + text;
    }

    /**
     * Extracts player rows from a status message.
     *
     * @param content raw status text.
     * @return parsed player lines.
     */
    private List<String> parsePlayersLines(String content) {
        List<String> players = new ArrayList<>();
        boolean inPlayers = false;
        for (String line : content.split("\\R")) {
            line = line.trim();
            if ("Players:".equalsIgnoreCase(line)) {
                inPlayers = true;
                continue;
            }
            if (inPlayers) {
                if (PLAYER_ROW_PATTERN.matcher(line).matches() || line.startsWith("You:")) {
                    players.add(line);
                } else if (line.isEmpty()) {
                    break;
                }
            }
        }
        return players;
    }

    /**
     * Parses all card row sections from the status payload.
     *
     * @param content raw status text.
     * @return map of row sections to parsed card displays.
     */
    private EnumMap<CardSection, List<CardDisplay>> parseStatusCardSections(String content) {
        EnumMap<CardSection, List<CardDisplay>> result = new EnumMap<>(CardSection.class);
        for (CardSection section : CardSection.values()) result.put(section, new ArrayList<>());

        CardSection current = null;
        for (String line : content.split("\\R")) {
            line = line.trim();
            if ("Upper tribe row:".equalsIgnoreCase(line)) current = CardSection.UPPER_TRIBE;
            else if ("Upper buildings:".equalsIgnoreCase(line)) current = CardSection.UPPER_BUILDINGS;
            else if ("Lower tribe row:".equalsIgnoreCase(line)) current = CardSection.LOWER_TRIBE;
            else if ("Lower buildings:".equalsIgnoreCase(line)) current = CardSection.LOWER_BUILDINGS;
            else if (current != null && line.startsWith("[")) {
                Integer assetId = extractAssetId(line);
                if (assetId != null) {
                    result.get(current).add(new CardDisplay(assetId, line));
                }
            }
        }
        return result;
    }

    /**
     * Extracts an image asset id from a formatted status line.
     *
     * @param line source line.
     * @return parsed asset id, or {@code null} if absent/invalid.
     */
    private Integer extractAssetId(String line) {
        Matcher matcher = IMG_ID_PATTERN.matcher(line);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } 
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** Disconnects the active connection without surfacing UI errors. */
    private void disconnectSilently() {
        ConnectionContext ctx = activeConnection.getAndSet(null);
        if (ctx != null) ctx.disconnect();
        
        // Reset UI notification state
        notificationQueue.clear();
        isNotificationPlaying = false;
    }

    // =========================================================================
    // INNER CLASSES (Networking)
    // =========================================================================

    /**
     * Common transport abstraction used by the GUI for socket and RMI sessions.
     */
    private interface ConnectionContext {
        /** @return nickname bound to this connection. */
        String getNickname();
        /** @return {@code true} if transport is currently connected. */
        boolean isConnected();
        /**
         * Sends an action to the server.
         *
         * @param action action payload.
         * @return optional immediate response.
         * @throws IOException if transport write/call fails.
         */
        SerializedUpdate send(ActionMessage action) throws IOException;
        /** Closes transport resources and unregisters callbacks. */
        void disconnect();
        /** Starts periodic heartbeat updates for the transport. */
        void startHeartbeat();
    }

    /**
     * Socket-backed connection context for GUI actions and heartbeat handling.
     */
    private final class SocketConnectionContext implements ConnectionContext {
        private final Socket socket;
        private final PrintWriter out;
        private String nickname;
        private final ObjectMapper socketMapper = new ObjectMapper();
        private ScheduledExecutorService heartbeatExecutor;
        private ClientThread listener;

        /**
         * Creates the socket connection context.
         *
         * @param socket socket transport.
         * @param out socket writer.
         * @param nickname player nickname.
         */
        private SocketConnectionContext(Socket socket, PrintWriter out, String nickname) {
            this.socket = socket;
            this.out = out;
            this.nickname = nickname;
        }

        /** {@inheritDoc} */
        @Override public String getNickname() { return nickname; }
        /** {@inheritDoc} */
        @Override public boolean isConnected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

        /** {@inheritDoc} */
        @Override
        public SerializedUpdate send(ActionMessage action) throws IOException {
            out.println(socketMapper.writeValueAsString(action));
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public void disconnect() {
            if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
            try { if (listener != null) listener.interrupt(); } catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
        }

        /** {@inheritDoc} */
        @Override
        public void startHeartbeat() {
            if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
                return;
            }
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (isConnected()) {
                    try { send(new ActionMessage("HEARTBEAT", nickname, 0)); } 
                    catch (IOException e) { heartbeatExecutor.shutdownNow(); }
                }
            }, 10, 10, TimeUnit.SECONDS);
        }
    }

    /**
     * RMI-backed connection context for GUI actions and heartbeat handling.
     */
    private final class RmiConnectionContext implements ConnectionContext {
        private final GameServiceRemote service;
        private RmiCallback callback;
        private String nickname;
        private ScheduledExecutorService heartbeatExecutor;

        /**
         * Creates the RMI connection context.
         *
         * @param service RMI game service proxy.
         * @param callback exported callback instance.
         * @param nickname player nickname.
         */
        private RmiConnectionContext(GameServiceRemote service, RmiCallback callback, String nickname) {
            this.service = service;
            this.callback = callback;
            this.nickname = nickname;
        }

        /** {@inheritDoc} */
        @Override public String getNickname() { return nickname; }
        /** {@inheritDoc} */
        @Override public boolean isConnected() { return service != null; }

        /** {@inheritDoc} */
        @Override
        public SerializedUpdate send(ActionMessage action) throws IOException {
            SerializedUpdate response = service.sendAction(action);
            if (response != null && !"NOTIFICATION".equalsIgnoreCase(response.getType())) {
                Platform.runLater(() -> handleIncomingUpdate(response));
            }
            return response;
        }

        /** {@inheritDoc} */
        @Override
        public void disconnect() {
            if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
            try { service.disconnect(nickname); } catch (Exception ignored) {}
            try { UnicastRemoteObject.unexportObject(callback, true); } catch (Exception ignored) {}
        }

        /** {@inheritDoc} */
        @Override
        public void startHeartbeat() {
            if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
                return;
            }
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                try { service.heartbeat(nickname); } 
                catch (Exception e) { heartbeatExecutor.shutdownNow(); }
            }, 10, 10, TimeUnit.SECONDS);
        }
    }

    /**
     * RMI callback bridge that forwards server updates to the JavaFX client.
     */
    private static final class RmiCallback extends UnicastRemoteObject implements ClientCallbackRemote {
        private final Consumer<SerializedUpdate> updateObserver;

        /**
         * Creates an exported RMI callback implementation.
         *
         * @param updateObserver consumer that handles incoming updates.
         * @throws IOException if export fails.
         */
        private RmiCallback(Consumer<SerializedUpdate> updateObserver) throws IOException {
            super();
            this.updateObserver = updateObserver;
        }

        /** {@inheritDoc} */
        @Override
        public void onUpdate(SerializedUpdate update) {
            if (update != null && updateObserver != null) {
                updateObserver.accept(update);
            }
        }
    }

    /** Card rows that can be rendered from a status update. */
    private enum CardSection { UPPER_TRIBE, UPPER_BUILDINGS, LOWER_TRIBE, LOWER_BUILDINGS }

    /** Lightweight display model for a card image and tooltip text. */
    private static final class CardDisplay {
        private final int assetId;
        private final String tooltip;

        /**
         * Creates a card display model.
         *
         * @param assetId card image asset id.
         * @param tooltip tooltip text shown for the card.
         */
        private CardDisplay(int assetId, String tooltip) {
            this.assetId = assetId;
            this.tooltip = tooltip;
        }
    }
}
