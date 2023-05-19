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

/**
 * Главный класс программы
 */
public class Main extends Application {

    /** Стандартная ширина окна приложения */
    private static final int APP_WINDOW_WIDTH = 800;

    /** Количество ячеек в сетке */
    private static final int ROWS_AMOUNT = 50;
    private static final int WIDTH = APP_WINDOW_WIDTH;

    /** Элемент Pane, на котором отрисовывается сетка */
    private Pane pane;

    /** Основная сцена*/
    Scene scene;

    /** Основная сцена-контейнер*/
    public Stage primaryStage;

    /** Переменная нужна для хранения абсалютного пути к рабочим файлам (пользовательским картам) */
    public String currentFilesBMP;

    /** Объект соеденения с базой данных */
    Connection conn;

    /** Объект image класса  BufferedImage нужен для корректной работы методов ответственных за загрузку и сохранение карт из файлов */
    BufferedImage image = new BufferedImage(ROWS_AMOUNT, ROWS_AMOUNT, BufferedImage.TYPE_INT_RGB);
    private Rectangle[][] rects = new Rectangle[ROWS_AMOUNT][ROWS_AMOUNT];

    /**
     Возвращает ближайший к месту клика ячейку (Spot)

     @param y Координата клика по оси y.

     @param x Координата клика по оси x.

     @return Массив целых чисел, содержащий номер строки и столбца, по которым можно найти ячейку (Spot) в сетке.
     */
    public static int[] getClickedPos(double y, double x) {
        int spotSize = WIDTH / ROWS_AMOUNT;

        int row = (int) y / spotSize;
        int col = (int) x / spotSize;

        return new int[]{row, col};
    }

    /**
     Запускает приложение и инициализирует графический интерфейс.

     @param stage Объект Stage для отображения графического интерфейса.

     @throws Exception В случае возникновения исключительной ситуации при запуске приложения.
     */
    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        /** Количество строк в массиве точек графа*/
        int rows = ROWS_AMOUNT;
        /** Количество строк в массиве точек графа*/
        int width = APP_WINDOW_WIDTH;
        /** Размер отдельной точки графа*/
        int spotSize = width / rows;


        /** Создание базы данных для хранения пользовательских карт.*/
        conn = new H2DatabaseConnection().getConnection();

        Statement stmt = conn.createStatement();

        /** Создание таблицы для хранения пользовательских карт.*/
        stmt.execute("CREATE TABLE IF NOT EXISTS maps (id INT AUTO_INCREMENT PRIMARY KEY, image VARCHAR)");

        /** Создание меню и его элементов. Добавление обработчиков для этих элементов. */

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
        /** Отрисовка сетки */
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

    /**
     * Точка входа в программу
     * @param args
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     Класс, представляющий ячейку сетки.
     */
    public class Spot {
        /** Номер строки ячейки*/
        public int row;

        /** Номер столбца ячейки*/
        public int col;

        /** Координата x. Нужна для отслеживания нажатий мыши*/
        public int x;

        /** Координата y. Нужна для отслеживания нажатий мыши*/
        public int y;

        /** Переменная цвета. Используется для предания цвета ячейкам сетки*/
        public Color color = Color.WHITE;

        /** Массив объектов Spot, который хранит соседние ячейки (рёбра) для текущей ячейки */
        public List<Spot> neighbors = new ArrayList<>();

        /** Шырина одной ячейки */
        public int width;

        /** Общее количество строк в сетке. */
        public int totalRows;

        /**
         Конструктор класса Spot.
         @param row Номер строки ячейки.
         @param col Номер столбца ячейки.
         @param width Ширина ячейки.
         @param totalRows Общее количество строк в сетке.
         */
        public Spot(int row, int col, int width, int totalRows) {
            this.row = row;
            this.col = col;
            this.width = width;
            this.totalRows = totalRows;
            this.x = row * width;
            this.y = col * width;
        }

        /**
         Возвращает позицию ячейки.
         @return Массив из двух целых чисел, содержащий номер строки и столбца ячейки.
         */
        public int[] getPos() {
            return new int[] { row, col };
        }

        /**
         Проверяет, является ли ячейка препятствием.
         @return true, если ячейка является препятствием, иначе false.
         */
        public boolean isBarrier() {
            return color == Color.BLACK;
        }

        /**
         Сбрасывает цвет ячейки на белый. Нужно при очистке сетки
         */
        public void reset() {
            color = Color.WHITE;
        }

        /**
         Устанавливает цвет ячейки как у начальной точки.
         */
        public void makeStart() {
            color = Color.ORANGE;
        }

        /**
         Устанавливает цвет ячейки как закрытую. Если ячейка помечена как закрытая, значит алогритм A* уже перебрал её
         */
        public void makeClosed() {
            color = Color.RED;
        }

        /**
         Устанавливает цвет ячейки как откытую. Если ячейка помечена как открытая, значит алогритм A* уже рассматривает
         её как потециальное ребро маршрута
         */
        public void makeOpen() {
            color = Color.GREEN;
        }

        /**
         Устанавливает цвет ячейки как препятствие.
         */
        public void makeBarrier() {
            color = Color.BLACK;
        }

        /**
         Устанавливает цвет ячейки как конечную точку.
         */
        public void makeEnd() {
            color = Color.TURQUOISE;
        }
        /**
         Устанавливает цвет ячейки как путь т.е как одно из рёбер кратчайшего пути.
         */
        public void makePath() {
            color = Color.PURPLE;
        }

        /**
         Обновляет список соседних ячеек для текущей ячейки.

         @param grid Двумерный массив ячеек, представляющий сетку.
         */
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

    /**
     * Эвристическая функция сообщает алгоритму A* оценку минимальной стоимости пути от любой вершины n до цели.

     @param p1 Координаты первой точки в виде массива [x, y].

     @param p2 Координаты второй точки в виде массива [x, y].

     @return Эвристическая оценка расстояния между двумя точками.
     */
    public static int h(int[] p1, int[] p2) {
        int x1 = p1[0];
        int y1 = p1[1];
        int x2 = p2[0];
        int y2 = p2[1];
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /**
     Восстанавливает путь по картам предшествования от конечной точки до начальной точки.
     @param cameFrom Мапа, содержащая информацию о предшествующих ячейках для каждой ячейки.
     @param current Текущая ячейка (конечная точка).
     */
    public static void reconstructPath(Map<Spot, Spot> cameFrom, Spot current) {
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            current.makePath();
        }
    }

    /**
     * Метод checkLoad представляет диалоговое окно для выбора файла из файловой системы.
     * Возвращает true, если файл был выбран, и false, если выбор файла был отменен.
     *
     * @return true, если файл был выбран; false, если выбор файла был отменен
     */
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

    /**
     * Метод загружает сохраненную сетку ячеек из изображения.
     * Создает новую сетку ячеек типа Spot[][] и заполняет ее значениями на основе цветов пикселей в изображении.
     * Черный цвет (java.awt.Color.BLACK) соответствует барьеру в ячейке, а любой другой цвет соответствует свободной ячейке.
     * Загруженная сетка возвращается в качестве результата.

     * @return Возращает реконструированную сетку
     */
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

    /**
     * Метод сохраняет карту в формате BMP. Она открывает диалоговое окно для выбора места сохранения файла, а затем создает новое изображение типа BufferedImage и заполняет его цветами ячеек из текущей сетки.
     * Черный цвет соответствует барьеру, а белый цвет - свободной ячейке. Затем изображение сохраняется в выбранном файле.
     */
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

    /**
     *Метод сохраняет карту в базу данных. Она создает изображение типа BufferedImage и заполняет его цветами ячеек из текущей сетки.
     * Затем изображение конвертируется в формат Base64 для хранения в БД. Наконец, картинка добавляется в таблицу "maps" в базе данных.
     */
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

        /** Конвертация BMP изображения в Base64 формат для их хранения в базу данных */
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String base64Image = null;
        try {
            ImageIO.write(image, "bmp", baos);
            byte[] imageData = baos.toByteArray();
            base64Image = Base64.getEncoder().encodeToString(imageData);
        }catch (IOException e){
            System.out.println(e.fillInStackTrace());
        }

        /** Добавление пользовательской карты в базу данных */
        try{
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO maps (image) VALUES (?)");
            pstmt.setString(1, base64Image);
            pstmt.executeUpdate();
        }catch (SQLException e){
            System.out.println(e.fillInStackTrace());
        }
    }

    /**
     * Метод очищает карту, сбрасывая начальную и конечную точки на `null` и восстанавливая сетку путем создания новой сетки с помощью функции `makeGrid`.
     * Затем вызывается метод `repaintSpots()`, чтобы перерисовать ячейки на карту.
     */
    public void clearMap(){
        start = null;
        end = null;
        grid = makeGrid(ROWS_AMOUNT, WIDTH);
        repaintSpots();
    }

    /**
     *Метод отображает окно со списком доступных карт из базы данных. Он выполняет следующие шаги:
     1. Создает окно (`Stage`) с заголовком "Список доступных карт".
     2. Создает `ListView` для отображения карт и `ObservableList` для хранения элементов списка.
     3. Выполняет SQL-запрос к базе данных, получает карты и добавляет их в `ObservableList` в виде `ImageView`.
     4. Заполняет `ListView` элементами из `ObservableList`.
     5. Устанавливает обработчик нажатия на элементы `ListView`, чтобы при выборе карты происходило ее загрузка и отображение на карте.
     6. Создает сцену (`Scene`) для отображения списка карт с заданными размерами.
     7. Устанавливает созданную сцену на окно (`Stage`) и отображает окно.

     * @throws SQLException если возникает ошибка при выполнении SQL-запроса к базе данных.
     */
    private void displayMapsWindow() throws SQLException {

        Stage imageWindow = new Stage();
        imageWindow.setTitle("Список доступных карт");

        /** Создание списка для отображения карт */
        ListView<ImageView> listView = new ListView<>();
        ObservableList<ImageView> imageList = FXCollections.observableArrayList();

        /** Получение карт из БД */
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM maps");
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            byte[] imageBytes = Base64.getDecoder().decode(rs.getString(2));
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            Image image = new Image(bis);

            ImageView imageView = new ImageView(image);
            imageList.add(imageView);
        }

        listView.setItems(imageList);


        /** Обработчики нажатий по элементам ListView */
        listView.setOnMouseClicked(event -> {
            /** Получение выбранной пользовательской карты */
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

        /** Сцена для отображения списка карт */
        Scene imageScene = new Scene(listView, 400, 400);
        imageWindow.setScene(imageScene);
        imageWindow.show();
    }

    /**
     *
     Метод выполняет A* алгоритм для поиска пути от начальной точки до конечной точки на сетке карты.
     Он использует приоритетную очередь (`PriorityQueue`) для хранения открытого множества вершин.
     В цикле выполняется поиск пути, обновление значений g- и f-скоров, добавление соседних вершин в открытое множество и обновление цвета вершин.
     Если достигнута конечная точка, происходит восстановление пути и установка соответствующего цвета вершины.
     @param start начальная точка
     @param end конечная точка
     */
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

    /**
     *
     Метод создает сетку карты, состоящую из объектов Spot, которые представляют точки на сетке. Он принимает общее количество строк в сетке (`totalRow`) и ширину сетки (`width`). Затем он создает двумерный массив `gr` размером `totalRow x totalRow` и заполняет его новыми объектами Spot, используя указанные параметры. Каждая точка имеет размер (`spotSize`) и общее количество строк (`totalRow`) для дальнейшего использования. В результате метод возвращает созданную сетку карты.
     @param totalRow общее количество строк в сетке

     @param width ширина сетки

     @return двумерный массив объектов Spot, представляющих точки сетки
     */
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

    /**
     * Метод перерисовывает отображение всех точек на сетке карты.
     * Он проходит по каждой строке и каждой точке в сетке и вызывает метод `changeSpotColour` для каждой точки, чтобы обновить ее цвет в соответствии с ее текущим состоянием.
     */
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

    /**
     * Метод обрабатывает события, связанные с мышью и клавиатурой.
     * Он проверяет тип события и выполняет соответствующие действия. Если это событие мыши, то обрабатываются нажатия левой и правой кнопок мыши.
     * При нажатии левой кнопки мыши устанавливается начальная точка (start), конечная точка (end) или создается барьерная точка (makeBarrier) в зависимости от текущего состояния.
     * При нажатии правой кнопки мыши точка сбрасывается в исходное состояние (reset). Если это событие клавиатуры, то проверяется нажатие пробела (KeyCode.SPACE) для запуска алгоритма и нажатие клавиши "C" (KeyCode.C) для очистки карты.
     */
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
