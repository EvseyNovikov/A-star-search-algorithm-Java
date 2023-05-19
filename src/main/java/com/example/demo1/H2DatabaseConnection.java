package com.example.demo1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Класс H2DatabaseConnection представляет подключение к базе данных H2.
 * Использует шаблон проектирования Singleton для гарантированного создания только одного экземпляра подключения.
 */
public class H2DatabaseConnection {
    private static H2DatabaseConnection instance;
    private Connection connection;

    /**
     * Приватный конструктор класса H2DatabaseConnection.
     * Инициализирует подключение к базе данных H2.
     */
    H2DatabaseConnection() {
        try {
            connection = DriverManager.getConnection("jdbc:h2:~/mapsBD");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Получает экземпляр класса H2DatabaseConnection.
     * Если экземпляр не существует, создает новый экземпляр в потокобезопасном режиме.
     *
     * @return экземпляр класса H2DatabaseConnection
     */
    public static H2DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (H2DatabaseConnection.class) {
                if (instance == null) {
                    instance = new H2DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Возвращает подключение к базе данных H2.
     *
     * @return объект Connection, представляющий подключение к базе данных
     */
    public Connection getConnection() {
        return connection;
    }
}
