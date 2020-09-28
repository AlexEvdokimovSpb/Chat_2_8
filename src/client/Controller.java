package client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public HBox authPanel;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;

    private final String IP_ADDRESS = "localhost";
    private final int PORT = 8189;


    private Socket socket;
    DataInputStream in;
    DataOutputStream out;

    private File file;
    private DataOutputStream outToFile;
    private ArrayList <String> history; // лист для загрузки истории
    private boolean historyLoaded = false; // переменная по которой определяем загружалась ли история
    private int sizeHistory = 100; // ограничение размера выводимой истории

    private boolean authenticated;
    private String nickname;
    private final String TITLE = "Флудилка";

    private Stage stage;
    private Stage regStage;
    private RegController regController;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);

        if (!authenticated) {
            nickname = "";
        }
        textArea.clear();
        setTitle(nickname);

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthenticated(false);
        createRegWindow();
        Platform.runLater(() -> {
            stage = (Stage) textField.getScene().getWindow();
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    System.out.println("bye");
                    if (socket != null && !socket.isClosed()) {
                        try {
                            out.writeUTF("/end");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });
    }

    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //цикл аутентификации
                        while (true) {
                            String str = in.readUTF();

                            if (str.startsWith("/authok")) {
                                nickname = str.split(" ", 2)[1];

                                // готовим файл с указанным названием
                                file = new File("history_"+nickname+".txt");
                                if (!file.exists()){
                                    file.createNewFile();
                                }

                                // готовим поток для записи в файл
                                outToFile = new  DataOutputStream(new FileOutputStream(file, true));

                                history = new ArrayList();
                                try (BufferedReader inFromFile = new BufferedReader (new FileReader(file))) {
                                    String line = inFromFile.readLine();
                                    while (line != null) {
                                        history.add(line); // построчное копирование из файла истории в arraylist
                                        line = inFromFile.readLine();
                                    }
                                }


                                setAuthenticated(true);
                                break;
                            }

                            if (str.startsWith("/regok")) {
                                regController.addMsgToTextArea("Регистрация прошла успешно");
                            }
                            if (str.startsWith("/regno")) {
                                regController.addMsgToTextArea("Регистрация не получилась \n возможно логин или ник заняты");
                            }
                            textArea.appendText(str + "\n");
                        }

                        //цикл работы
                        while (true) {
                            String str = in.readUTF();

                            if (!historyLoaded){
                                if (history.size()>sizeHistory) { // проверяем, если история больше размера вывода
                                    for (int i = (history.size()-sizeHistory); i < history.size(); i++) {
                                        textArea.appendText(history.get(i) + "\n");
                                    }
                                }  else { // иначе
                                    for (int i = 0; i < history.size(); i++) {
                                        textArea.appendText(history.get(i) + "\n");
                                    }
                                }
                                historyLoaded=true;
                            }

                            if (str.startsWith("/")) {
                                if (str.equals("/end")) {
                                    break;
                                }
                                if (str.startsWith("/clientlist ")) {
                                    String[] token = str.split("\\s+");
                                    Platform.runLater(() -> {
                                        clientList.getItems().clear();
                                        for (int i = 1; i < token.length; i++) {
                                            clientList.getItems().add(token[i]);
                                        }
                                    });
                                }
                            } else {
                                textArea.appendText(str + "\n");
                                outToFile.writeUTF(str + "\n"); // отправляем полученное в файл
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        System.out.println("Мы отключились от сервера");
                        setAuthenticated(false);
                        try {
                            outToFile.close();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(ActionEvent actionEvent) {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(String.format("/auth %s %s", loginField.getText().trim().toLowerCase(),
                    passwordField.getText().trim()));
            passwordField.clear();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setTitle(String nick) {
        Platform.runLater(() -> {
            ((Stage) textField.getScene().getWindow()).setTitle(TITLE + " " + nick);
        });
    }

    public void clickClientList(MouseEvent mouseEvent) {
        String receiver = clientList.getSelectionModel().getSelectedItem();
        textField.setText("/w " + receiver + " ");
    }

    private void createRegWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("reg.fxml"));
            Parent root = fxmlLoader.load();
            regStage = new Stage();
            regStage.setTitle("Reg window");
            regStage.setScene(new Scene(root, 400, 250));

            regController = fxmlLoader.getController();
            regController.setController(this);

            regStage.initModality(Modality.APPLICATION_MODAL);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void registration(ActionEvent actionEvent) {
        regStage.show();
    }

    public void tryToReg(String login, String password, String nickname) {
        String msg = String.format("/reg %s %s %s", login, password, nickname);

        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }


        System.out.println(msg);
    }
}
