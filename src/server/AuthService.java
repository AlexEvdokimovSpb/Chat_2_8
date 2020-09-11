package server;

public interface AuthService {
    /**
     * @return nickname если пользователь есть
     * @return null если пользоватаеля нет
     * */
    String getNicknameByLoginAndPassword(String login, String password);

    boolean registration(String login, String password, String nickname);
}
