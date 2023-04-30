package com.example.demo1;

import java.io.*;
import java.util.*;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.event.EventType;
import javafx.scene.layout.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.scene.image.*;
import java.io.IOException;
import javafx.stage.FileChooser;
import java.sql.*;

public class Main extends Application {

    private static final int APP_WINDOW_WIDTH = 800;
    private static final int ROWS_AMOUNT = 50;
    private static final int WIDTH = APP_WINDOW_WIDTH;
    private Pane pane; // Тут отрисовывается карта
    Scene scene;
    public Stage primaryStage;
    public String currentFilesBMP;

    Connection conn;
    BufferedImage image = new BufferedImage(ROWS_AMOUNT, ROWS_AMOUNT, BufferedImage.TYPE_INT_RGB);
    private Rectangle[][] rects = new Rectangle[ROWS_AMOUNT][ROWS_AMOUNT];

    public static int[] getClickedPos(double y, double x) {
        int spotSize = WIDTH / ROWS_AMOUNT;

        int row = (int) y / spotSize;
        int col = (int) x / spotSize;

        return new int[]{row, col};
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        int rows = ROWS_AMOUNT;
        int width = APP_WINDOW_WIDTH;
        int spotSize = width / rows;

        // Создание базы данных для хранения пользовательских карт.
        conn = DriverManager.getConnection("jdbc:h2:~/mapsBD");

        // Создание таблицы для хранения пользовательских карт.

        Statement stmt = conn.createStatement();

        // Debug
//        stmt.executeUpdate("DROP TABLE maps");

        stmt.execute("CREATE TABLE IF NOT EXISTS maps (id INT AUTO_INCREMENT PRIMARY KEY, image VARCHAR)");

        // Создание меню и его элементов. Добавление обработчиков для этих элементов.
        Menu fileMenuItem = new Menu("Файл");

        MenuItem loadMapMenuItem = new MenuItem("Загрузить карту из фото");
        MenuItem saveMapMenuItem = new MenuItem("Сохранить карту как фото");
        MenuItem saveMapDBMenuItem = new MenuItem("Сохранить карту в БД");
        MenuItem loadMapDBMenuItem = new MenuItem("Загрузить карту из БД");

        loadMapMenuItem.setOnAction ( e -> {
            if(checkLoad()){
                grid = LoadSavedGrid();
                repaintSpots();
            }
        });

        saveMapMenuItem.setOnAction ( e -> {
            saveMapBMP();
        });

        saveMapDBMenuItem.setOnAction ( e -> {
            saveMapToDb();
        });

        loadMapDBMenuItem.setOnAction ( e -> {
            try {
                displayMapsWindow();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        fileMenuItem.getItems().add(loadMapMenuItem);
        fileMenuItem.getItems().add(saveMapMenuItem);
        fileMenuItem.getItems().add(saveMapDBMenuItem);
        fileMenuItem.getItems().add(loadMapDBMenuItem);

        Menu m_edit = new Menu("Правка");
        MenuItem m1_edit = new MenuItem("Очистить карту");
        m1_edit.setOnAction( e -> {
            clearMap();
        });

        m_edit.getItems().add(m1_edit);

        MenuBar menuBar = new MenuBar();

        menuBar.getMenus().add(fileMenuItem);
        menuBar.getMenus().add(m_edit);

        pane = new Pane();

        var lines = new ArrayList<Line>();

        // Отрисовка сетки
        for (int i = 0; i < rows; i++) {
            Line line = new Line(0, i * spotSize, width, i * spotSize);
            line.setStroke(Color.GREY); // Горизонтальные линии
            lines.add(line);

            for (int j = 0; j < rows; j++) {
                Line line2 = new Line(j * spotSize, 0, j * spotSize, width);
                line2.setStroke(Color.GREY);  // Вертикальные линии
                lines.add(line2);

                Spot spot = grid[i][j];
                Rectangle rect = new Rectangle(spot.x, spot.y, spot.width, spot.width);
                rect.setFill(Color.WHITE);
                rects[i][j] = rect;
                pane.getChildren().add(rect);
            }
        }

        for (var l : lines) {
            pane.getChildren().add(l);
        }

        BorderPane rootNode = new BorderPane();

        scene = new Scene(rootNode, WIDTH, 600);

        rootNode.setTop(menuBar);
        rootNode.setCenter(pane);

        scene.setOnMouseClicked(this::handleEvent);
        scene.setOnMousePressed(this::handleEvent);
        scene.setOnMouseDragged(this::handleEvent);

        scene.setOnKeyPressed(this::handleEvent);

        stage.setTitle("A* Path Finding Algorithm");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public class Spot {
        public int row;
        public int col;
        public int x;
        public int y;
        public Color color = Color.WHITE;
        public List<Spot> neighbors = new ArrayList<>();
        public int width;
        public int totalRows;

        public Spot(int row, int col, int width, int totalRows) {
            this.row = row;
            this.col = col;
            this.width = width;
            this.totalRows = totalRows;
            this.x = row * width;
            this.y = col * width;
        }

        public int[] getPos() {
            return new int[] { row, col };
        }

        public boolean isBarrier() {
            return color == Color.BLACK;
        }

        public void reset() {
            color = Color.WHITE;
        }

        public void makeStart() {
            color = Color.ORANGE;
        }

        public void makeClosed() {
            color = Color.RED;
        }

        public void makeOpen() {
            color = Color.GREEN;
        }

        public void makeBarrier() {
            color = Color.BLACK;
        }

        public void makeEnd() {
            color = Color.TURQUOISE;
        }

        public void makePath() {
            color = Color.PURPLE;
        }

        public void updateNeighbors(Spot[][] grid) {
            neighbors.clear();

            if (row < (totalRows - 1) && !grid[row + 1][col].isBarrier()) {
                neighbors.add(grid[row + 1][col]);
            }

            if (row > 0 && !grid[row - 1][col].isBarrier()) {
                neighbors.add(grid[row - 1][col]);
            }

            if (col < (totalRows - 1) && !grid[row][col + 1].isBarrier()) {
                neighbors.add(grid[row][col + 1]);
            }

            if (col > 0 && !grid[row][col - 1].isBarrier()) {
                neighbors.add(grid[row][col - 1]);
            }
        }
    }

    public static int h(int[] p1, int[] p2) {
        int x1 = p1[0];
        int y1 = p1[1];
        int x2 = p2[0];
        int y2 = p2[1];
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    public static void reconstructPath(Map<Spot, Spot> cameFrom, Spot current) {
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            current.makePath();
        }
    }

    public boolean checkLoad(){
        final FileChooser fileChooser = new FileChooser();
        File selectedDirectory = fileChooser.showOpenDialog(primaryStage);

        if(selectedDirectory == null){
            return false ;
        }else{
            currentFilesBMP = selectedDirectory.getAbsolutePath();
            return true;
        }
    }

    public Spot[][] LoadSavedGrid(){
        clearMap();

        int totalRow = ROWS_AMOUNT;
        int width = APP_WINDOW_WIDTH;
        Spot[][] gr = new Spot[totalRow][totalRow];
        int spotSize = width / totalRow;

        try {
            image = ImageIO.read(new File(currentFilesBMP));

            for (int i = 0; i < totalRow; i++) {
                for (int j = 0; j < totalRow; j++) {
                    gr[i][j] = new Spot(i, j, spotSize, totalRow);

                    if(image.getRGB(i, j) == java.awt.Color.BLACK.getRGB()){
                        gr[i][j].color = Color.BLACK;
                    }else{
                        gr[i][j].color = Color.WHITE;
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Not loading Images");
        }
        return gr;
    }

    public void saveMapBMP(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить карту как");
        fileChooser.setInitialFileName("Безымянная карта.bmp");

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BMP Images", "*.bmp"));
        File selectedDirectory = fileChooser.showSaveDialog(primaryStage);

        if(selectedDirectory == null){
            //Директория не была выбрана
            return;
        }else{
            BufferedImage image = new BufferedImage(ROWS_AMOUNT, ROWS_AMOUNT, BufferedImage.TYPE_INT_RGB);

            currentFilesBMP = selectedDirectory.getAbsolutePath();

            for (Spot[] row : grid) {
                for (Spot spot : row) {
                    if (grid[spot.row][spot.col].color == Color.BLACK){
                        image.setRGB(spot.row, spot.col, java.awt.Color.BLACK.getRGB());
                    } else {
                        image.setRGB(spot.row, spot.col, java.awt.Color.WHITE.getRGB());
                    }
                }
            }
            try {
                FileOutputStream fos = new FileOutputStream(currentFilesBMP);
                ImageIO.write(image, "bmp", fos);
                fos.close();
            }catch (IOException e){
                System.out.print("При сохранении карты в файл возникла ошибка.");
            }
        }
    }

    public void saveMapToDb(){
        BufferedImage image = new BufferedImage(ROWS_AMOUNT, ROWS_AMOUNT, BufferedImage.TYPE_INT_RGB);

        for (Spot[] row : grid) {
            for (Spot spot : row) {
                if (grid[spot.row][spot.col].color == Color.BLACK){
                    image.setRGB(spot.row, spot.col, java.awt.Color.BLACK.getRGB());
                } else {
                    image.setRGB(spot.row, spot.col, java.awt.Color.WHITE.getRGB());
                }
            }
        }

        // Конвертация BMP изображения в Base64 формат для их хранения в БД
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String base64Image = null;
        try {
            ImageIO.write(image, "bmp", baos);
            byte[] imageData = baos.toByteArray();
            base64Image = Base64.getEncoder().encodeToString(imageData);
        }catch (IOException e){
            System.out.println(e.fillInStackTrace());
        }

        // Добавление пользовательской карты в БД
        try{
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO maps (image) VALUES (?)");
            pstmt.setString(1, base64Image);
            pstmt.executeUpdate();
        }catch (SQLException e){
            System.out.println(e.fillInStackTrace());
        }
    }

    public void clearMap(){
        start = null;
        end = null;
        grid = makeGrid(ROWS_AMOUNT, WIDTH);
        repaintSpots();
    }

    private void displayMapsWindow() throws SQLException {

        Stage imageWindow = new Stage();
        imageWindow.setTitle("Список доступных карт");

        // Создание списка для отображения карт
        ListView<ImageView> listView = new ListView<>();
        ObservableList<ImageView> imageList = FXCollections.observableArrayList();

        // Получение карт из БД
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM maps");
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            byte[] imageBytes = Base64.getDecoder().decode(rs.getString(2));
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            Image image = new Image(bis);

            ImageView imageView = new ImageView(image);
            imageList.add(imageView);
        }

        // Заполнение ListView
        listView.setItems(imageList);


        // Обработчики нажатий по элементам ListView
        listView.setOnMouseClicked(event -> {
            // Получение выбранной пользовательской карты
            ImageView selectedImage = listView.getSelectionModel().getSelectedItem();
            if (selectedImage != null) {
                clearMap();

                BufferedImage bImage = SwingFXUtils.fromFXImage(selectedImage.getImage(), null);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                InputStream inputStream = null;
                try {
                    ImageIO.write(bImage, "bmp", outputStream);
                    byte[] res  = outputStream.toByteArray();
                    inputStream = new ByteArrayInputStream(res);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                int totalRow = ROWS_AMOUNT;
                int gridWidth = APP_WINDOW_WIDTH;

                Spot[][] gr = new Spot[totalRow][totalRow];
                int spotSize = gridWidth / totalRow;

                try {
                    image = ImageIO.read(inputStream);

                    for (int i = 0; i < totalRow; i++) {
                        for (int j = 0; j < totalRow; j++) {
                            gr[i][j] = new Spot(i, j, spotSize, totalRow);

                            if(image.getRGB(i, j) == java.awt.Color.BLACK.getRGB()){
                                gr[i][j].color = Color.BLACK;
                            }else{
                                gr[i][j].color = Color.WHITE;
                            }
                        }
                    }

                    grid = gr;
                    repaintSpots();
                } catch (IOException e) {
                    System.out.println("not loading Images");
                }
            }
        });

        // Сцена для отображения списка карт
        Scene imageScene = new Scene(listView, 400, 400);
        imageWindow.setScene(imageScene);
        imageWindow.show();
    }

    public void algorithm(Spot start, Spot end) {
        Comparator<Pair<Integer, Spot>> pairComparator = Comparator.comparing(Pair::getKey);

        PriorityQueue<Pair<Integer, Spot>> openSet = new PriorityQueue<>(pairComparator);
        openSet.add(new Pair<>(0, start));
        Map<Spot, Spot> cameFrom = new HashMap<>();
        Map<Spot, Integer> gScore = new HashMap<>();
        for (Spot[] row : grid) {
            for (Spot spot : row) {
                gScore.put(spot, Integer.MAX_VALUE);
            }
        }

        gScore.put(start, 0);
        Map<Spot, Integer> fScore = new HashMap<>();
        for (Spot[] row : grid) {
            for (Spot spot : row) {
                fScore.put(spot, Integer.MAX_VALUE);
            }
        }
        fScore.put(start, h(start.getPos(), end.getPos()));

        Set<Spot> openSetHash = new HashSet<>();
        openSetHash.add(start);

        while (!openSet.isEmpty()) {
            Pair<Integer, Spot> currentPair = openSet.poll();
            Spot current = currentPair.getValue();
            openSetHash.remove(current);

            if (current == end) {
                reconstructPath(cameFrom, end);
                end.makeEnd();
                return;
            }

            for (Spot neighbor : current.neighbors) {
                int tempGScore = gScore.get(current) + 1;
                if (tempGScore < gScore.get(neighbor)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tempGScore);
                    fScore.put(neighbor, tempGScore + h(neighbor.getPos(), end.getPos()));
                    if (!openSetHash.contains(neighbor)) {
                        openSet.add(new Pair<>(fScore.get(neighbor), neighbor));
                        openSetHash.add(neighbor);
                        neighbor.makeOpen();
                    }
                }
            }

            repaintSpots();

            if (current != start) {
                current.makeClosed();
            }
        }
    }

    private Spot start = null;
    private Spot end = null;
    private Spot[][] grid = makeGrid(ROWS_AMOUNT, WIDTH);
    private List<EventType<MouseEvent>> mouseEventTypes = new ArrayList<>(
            Arrays.asList(MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_CLICKED));

    public Spot[][] makeGrid(int totalRow, int width) {
        Spot[][] gr = new Spot[totalRow][totalRow];
        int spotSize = width / totalRow;

        for (int i = 0; i < totalRow; i++) {
            for (int j = 0; j < totalRow; j++) {
                gr[i][j] = new Spot(i, j, spotSize, totalRow);
            }
        }
        return gr;
    }

    private void repaintSpots() {
        for (Spot[] row : grid) {
            for (Spot spot : row) {
                changeSpotColour(spot);
            }
        }
    }

    private void changeSpotColour(Spot spot) {
        rects[spot.row][spot.col].setFill(spot.color);
    }

    private void handleEvent(Event event) {
        var eventType = event.getEventType();
        if (mouseEventTypes.contains(eventType)) {
            var mouseEvent = (MouseEvent) event;
            var mouseButton = mouseEvent.getButton();

            int[] pos = getClickedPos(mouseEvent.getX(), mouseEvent.getY());
            int row = pos[0];
            int col = pos[1]-1; // !Костыль!

            Spot spot = grid[row][col];

            if (mouseButton == MouseButton.PRIMARY) {
                if (start == null) {
                    start = spot;
                    start.makeStart();
                } else if (end == null && !spot.equals(start)) {
                    end = spot;
                    end.makeEnd();
                } else if (!spot.equals(end) && !spot.equals(start)) {
                    spot.makeBarrier();
                }
                repaintSpots();
            } else if (mouseButton == MouseButton.SECONDARY) {
                spot.reset();

                if (spot.equals(start)) {
                    start = null;
                } else if (spot.equals(end)) {
                    end = null;
                }
                repaintSpots();
            }
        } else if (eventType == KeyEvent.KEY_PRESSED) {
            KeyEvent keyEvent = (KeyEvent) event;
            var code = keyEvent.getCode();
            if (code == KeyCode.SPACE && start != null && end != null) {
                for (var row : grid) {
                    for (var spot : row) {
                        spot.updateNeighbors(grid);
                    }
                }

                algorithm(start, end);
                repaintSpots();
            } else if (code == KeyCode.C) {
                start = null;
                end = null;
                grid = makeGrid(ROWS_AMOUNT, WIDTH);
                repaintSpots();
            }
        }
    }
}