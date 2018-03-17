package com.mycompany.rsreubot;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.telegram.telegrambots.exceptions.*;
import org.telegram.telegrambots.*;
import org.telegram.telegrambots.api.methods.send.*;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.api.objects.replykeyboard.*;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.bots.*;
//import com.mysql.cj.jdbc.Driver;
import org.postgresql.Driver;
/**
 *
 * @author Админ
 */
public class TelegramBot extends TelegramLongPollingBot {

    static final String GET_SCHEDULE = "Получить расписание", GET_SUBSCRIBE = "Получить расписание по подписке",
            TIME = "Время пар", UPDATES = "Обновления", FEEDBACK = "Обратная связь", GETBACK = "Вернуться назад",
            SUBSCRIBE = "Подписаться на группу", TODAY = "Сегодня", TOMORROW = "Завтра", GETMAIN = "Вернуться в главное меню";
    static Connection conn;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new TelegramBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        connect();
    }

    public static void simpleAPIRequestShouldNotFail(Message message, String text) throws Exception {
//        try (CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
//            client.start();
//            Botan botan = new Botan(client, new ObjectMapper());
//            botan.track("0995c373-a844-4f4c-8c4a-f16fae64215e", message.getChatId().toString(), message, text).get();
//        };
    }

    @Override
    public String getBotUsername() {
        return "rsreu";
    }

    @Override
    public String getBotToken() {
        return "447648682:AAGLQPtIg__uNUGTDYzLcm_DI6Yb-QFt_A8";
    }

    @Override
    public void onUpdateReceived(Update update) {
        boolean isSendAnalytics = false;
        Message message = update.getMessage();
        if (message != null && message.hasText()) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.enableMarkdown(true);
            sendMessage.setChatId(message.getChatId().toString());
            System.out.println(message.getText());
            switch (message.getText()) {
                case "/start":
                    start(message);
                    break;
                case GET_SCHEDULE:
                    get_schedule(message, sendMessage);
                    break;
                case GET_SUBSCRIBE:
                    get_subscribe(message);
                    break;
                case TIME:
                    time(message);
                    break;
                case UPDATES:
                    updates(message);
                    break;
                case FEEDBACK:
                    feedback(message);
                    break;
                case SUBSCRIBE:
                    subscribe(message);
                    break;
                case TODAY:
                    get(message, sendMessage, false);
                    break;
                case TOMORROW:
                    get(message, sendMessage, true);
                    break;
                case GETBACK:
                    getback(message, sendMessage);
                    break;
                case GETMAIN:
                    if (checkstate(message) != null) {
                        clearState(message);
                    }
                    start(message);
                    break;
                default:
                    state st = checkstate(message);
                    if (st != null) {
                        switch (st) {
                            case faculty:
                                String id = checkInput("T_FACULTIES", message.getText());// Проверить факультет
                                if (id != "") {
                                    setKeyboard(sendMessage, "T_COURSES"); // Вывести список курсов
                                    setState(message, state.course, "faculty", id);// Ввести состояние пользователя(course) и факультет в T_USERS
                                    sendMsg(sendMessage, "Введите курс");
                                } else {
                                    sendMsg(sendMessage, "Введите факультет");
                                }
                                try {
                                    simpleAPIRequestShouldNotFail(message, "Выбор факультета");
                                    isSendAnalytics = true;
                                } catch (Exception e) {
                                }
                                break;
                            case course:
                                id = checkInput("T_COURSES", message.getText()); // Проверить курс
                                String idFaculty = getIdFaculty(message.getChatId().toString());
                                if (id != "") {
                                    setKeyboard(sendMessage, String.format("T_TEAMS WHERE course = %s AND faculty = %s", id, idFaculty)); // Вывести список групп
                                    setState(message, state.team, "course", id);// Ввести состояние пользователя(class) и курс в T_USERS
                                    sendMsg(sendMessage, "Введите группу");
                                } else {
                                    sendMsg(sendMessage, "Введите курс");
                                }
                                try {
                                    simpleAPIRequestShouldNotFail(message, "Выбор курса");
                                    isSendAnalytics = true;
                                } catch (Exception e) {
                                }
                                break;
                            case team:
                                String idTeam = checkInput("T_TEAMS", message.getText()); // Проверить группу
                                if (idTeam != "") {
                                    setState(message, state.day, "team", idTeam); // Ввести состояние пользователя(day) и группу в T_USERS
                                    setKeyboard(sendMessage, message.getChatId(), true); // Вывести список дней и подписку
                                    sendMsg(sendMessage, "Выберите действие");
                                } else {
                                    sendMsg(sendMessage, "Введите группу");
                                }
                                try {
                                    simpleAPIRequestShouldNotFail(message, "Выбор группы");
                                    isSendAnalytics = true;
                                } catch (Exception e) {
                                }
                                break;
                            case day:
                                break;
                        }
                    }
            }
//            if (!isSendAnalytics) {
//                try {
//                    simpleAPIRequestShouldNotFail(message, message.getText());
//                } catch (Exception e) {
//                    System.out.println(e.getMessage());
//                }
//            }
        }
    }

    void clearState(Message message) {
        disconnect();
        connect();
        Statement stmt;
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            String sql = String.format("UPDATE T_USERS SET (state, faculty, course, team)=(null, null, null, null) WHERE chatId = %d", message.getChatId());
            stmt.execute(sql);
            conn.commit();
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        disconnect();
    }

    String getIdFaculty(String chatId) {
        connect();
        Statement stmt;
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(String.format("SELECT faculty FROM T_USERS WHERE chatId = %s", chatId));
            if (rs.next()) {
                return rs.getString("faculty");
            }
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            disconnect();
        }
        return "";
    }

    private void get_schedule(Message message, SendMessage sendMessage) {
        setKeyboard(sendMessage, "T_FACULTIES");
        setState(message, state.faculty, "", "");
        sendMsg(sendMessage, "Выберите факультет");
    }

    void setState(Message message, state st, String column, String inColumn) {
        connect();
        Statement stmt;
            String sql="";
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            if (column == "") {

                sql = String.format("INSERT INTO T_USERS (chatId , state) VALUES  (%d, '%s') ON CONFLICT (chatId) DO UPDATE SET state = '%s';",
                        message.getChatId(), st.toString(), st.toString());
            } else {
                sql = String.format("INSERT INTO T_USERS (chatId, %s, state) VALUES (%d, '%s', '%s') ON CONFLICT (chatId) DO UPDATE SET (state, %s) = ('%s','%s');",
                         column, message.getChatId(), inColumn, st.toString(), column, st.toString(), inColumn);
            }
            stmt.execute(sql);
            conn.commit();
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage() +" "+ sql);
        } finally {
            disconnect();
        }
    }

    String checkInput(String tableName, String column) {
        connect();
        try {
            conn.setAutoCommit(true);
            Statement stmt = conn.createStatement();
            String sql = String.format("SELECT id FROM %s WHERE NAME LIKE '%s';", tableName, column);
            ResultSet rs = stmt.executeQuery(sql);

            boolean b = rs.next();
            if (b) {
                return rs.getString("ID");
            }
            stmt.close();
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        } finally {
            disconnect();
        }
        return "";
    }

    private void setKeyboard(SendMessage sendMessage, String nameTableForKeyboard) {
        connect();
        Statement stmt = null;
        List<KeyboardRow> keyboard = new ArrayList<>();
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(String.format("SELECT NAME FROM %s;", nameTableForKeyboard));
            KeyboardRow kr = new KeyboardRow();
            kr.add(new KeyboardButton(GETBACK));
            kr.add(new KeyboardButton(GETMAIN));
            keyboard.add(kr);
            while (rs.next()) {
                kr = new KeyboardRow();
                kr.add(new KeyboardButton(rs.getString("name")));
                if (rs.next()) {
                    kr.add(new KeyboardButton(rs.getString("name")));
                }
                keyboard.add(kr);
            }
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        replyKeyboardMarkup.setKeyboard(keyboard);
        disconnect();
    }

    private void setKeyboard(SendMessage sendMessage, long chatId, boolean subscribe) {
        connect();
        Statement stmt = null;

        List<KeyboardRow> keyboard = new ArrayList<>();
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        KeyboardRow kr = new KeyboardRow();
        if (subscribe) {
            kr.add(new KeyboardButton(GETBACK));
        }
        kr.add(new KeyboardButton(GETMAIN));
        keyboard.add(kr);
        kr = new KeyboardRow();
        kr.add(new KeyboardButton(TODAY));
        kr.add(new KeyboardButton(TOMORROW));
        keyboard.add(kr);
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(String.format("SELECT subscribe, team FROM T_USERS WHERE chatId = %d;", chatId));
            String sub = "", team = "";
            if (rs.next()) {
                sub = rs.getString("subscribe");
                team = rs.getString("team");
            }

            if (subscribe) {
                if (sub == null) {
                    kr = new KeyboardRow();
                    kr.add(new KeyboardButton(SUBSCRIBE));
                    keyboard.add(kr);
                } else if (!sub.equals(team)) {
                    kr = new KeyboardRow();
                    kr.add(new KeyboardButton(SUBSCRIBE));
                    keyboard.add(kr);
                }
            }

            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        replyKeyboardMarkup.setKeyboard(keyboard);
        disconnect();
    }

    void getback(Message message, SendMessage sendMessage) {
        state s = checkstate(message);
        if (s != null) {
            switch (s) {
                case faculty:
                    clearState(message);
                    start(message);
                    break;
                case course:
                    setKeyboard(sendMessage, "T_FACULTIES"); // Вывести список курсов
                    setState(message, state.faculty, "", "");// Ввести состояние пользователя(class) и курс в T_USERS
                    sendMsg(sendMessage, "Выберите факультет");
                    break;
                case team:
                    setKeyboard(sendMessage, "T_COURSES"); // Вывести список курсов
                    setState(message, state.course, "", "");// Ввести состояние пользователя(class) и курс в T_USERS
                    sendMsg(sendMessage, "Выберите курс");
                    break;
                case day:
                    setKeyboard(sendMessage, "T_TEAMS"); // Вывести список курсов
                    setState(message, state.team, "", "");// Ввести состояние пользователя(class) и курс в T_USERS
                    sendMsg(sendMessage, "Выберите группу");
                    break;
            }
        } else {
            start(message);
        }
    }

    private state checkstate(Message message) {
        connect();
        Statement stmt = null;
        state st = null;
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            String query = String.format("SELECT state FROM T_USERS WHERE chatId=%d;", message.getChatId());
            ResultSet rs = stmt.executeQuery(query);
            String s = "";
            if (rs.next()) {
                s = rs.getString("state");
            }
            if (s != null) {
                if (!s.equals("")) {
                    st = state.valueOf(s);
                }
            }
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            disconnect();
            return st;
        }
    }

    private void start(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        setKeyBoardMainMenu(sendMessage);
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.setText("Выберите пункт меню: ");
        try {
            sendMessage(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void get_subscribe(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(message.getChatId().toString());
        setKeyboard(sendMessage, message.getChatId(), false); // Вывести список дней и подписку
        sendMsg(sendMessage, "Выберите действие");
    }

    private void time(Message message) {
        try {
            sendSticker(new SendSticker().setSticker("CAADAgADAQADC83MFMGWSS3tgs_GAg").setChatId(message.getChatId()));
        } catch (TelegramApiException ex) {
            Logger.getLogger(TelegramBot.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void updates(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(message.getChatId().toString());
        setKeyBoardMainMenu(sendMessage);
        sendMsg(sendMessage, "Обновления и дополнительную информацию можно посмотреть тут — https://t.me/RsreuBotUpdate");
    }

    private void feedback(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(message.getChatId().toString());
        setKeyBoardMainMenu(sendMessage);
        sendMsg(sendMessage, "По всем вопросам и предложениям:\n • vilya1997@bk.ru\n • @piller97\n • vk.com/piller97");
    }

    private void sendMsg(SendMessage sendMessage, String text) {
        sendMessage.setText(text);
        try {
            sendMessage(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    void setKeyBoardMainMenu(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        // Создаем список строк клавиатуры
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Строки клавиатуры
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        KeyboardRow keyboardSecondRow = new KeyboardRow();
        KeyboardRow keyboardThirdRow = new KeyboardRow();
        KeyboardRow keyboardFourthRow = new KeyboardRow();
        keyboardFirstRow.add(GET_SCHEDULE);
        keyboardSecondRow.add(GET_SUBSCRIBE);
        keyboardThirdRow.add(TIME);
        keyboardFourthRow.add(UPDATES);
        keyboardFourthRow.add(FEEDBACK);

        // Добавляем все строчки клавиатуры в список
        keyboard.add(keyboardFirstRow);
        keyboard.add(keyboardSecondRow);
        keyboard.add(keyboardThirdRow);
        keyboard.add(keyboardFourthRow);
        // и устанваливаем этот список нашей клавиатуре
        replyKeyboardMarkup.setKeyboard(keyboard);
    }

    public static void connect() {
        try {
            DriverManager.registerDriver(new Driver());
            String url = "jdbc:postgresql://ec2-54-243-59-122.compute-1.amazonaws.com:5432/dcu5pfp749fojt?sslmode=require";
            //String url = "jdbc:postgresql://127.0.0.1:5432/postgres";
            conn = DriverManager.getConnection(url, "ucrydkwwdphedv", "ca50a9015ef5216ea43c14df4cdff56b33b09fa9884b84a6e737c264ff6536f4");

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void disconnect() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private void subscribe(Message message) {
        connect();
        Statement stmt;
        try {
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            String sql = String.format("UPDATE t_users t1 SET subscribe = t2.team FROM t_users t2  WHERE t1.chatId = %d AND t2.chatId = %d;", message.getChatId(), message.getChatId());
            stmt.execute(sql);
            conn.commit();
            SendMessage sendMessage = new SendMessage();
            sendMessage.enableMarkdown(true);
            sendMessage.setChatId(message.getChatId().toString());
            setKeyBoardMainMenu(sendMessage);
            sendMsg(sendMessage, "Вы успешно подписались на группу");
            clearState(message);
            stmt.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void get(Message message, SendMessage sendMessage, boolean tomorrow) {
        connect();
        String sql="";
        try {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            long id_group = -1;
            state st = checkstate(message);
            if (st != null) {

                sql = String.format("SELECT team FROM T_USERS WHERE chatId = %d", message.getChatId());
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    id_group = Integer.parseInt(rs.getString("team"));
                } else {
                    return;
                }
            } else {

                sql = String.format("SELECT \"subscribe\" FROM T_USERS WHERE chatId = %d", message.getChatId());
                ResultSet rs = stmt.executeQuery(sql);
                System.out.println(sql);
                if (rs.next()) {
                    id_group = Integer.parseInt(rs.getString("subscribe"));
                } else {
                    sendMsg(sendMessage, "Вы не подписаны ни на одну группу");
                    return;
                }
            }
            int weekNum = getWeekNum(tomorrow);
            int kind = weekNum % 2 == 0 ? 1 : 2;
            int change = getIsChangeDay(tomorrow);
            sql = String.format("SELECT discipline, start FROM T_SCHEDULE WHERE id_group = %d AND day = '%s' AND (kind = '%d' OR kind = '0') AND (change = '%d' or change = '0') ORDER BY start;", id_group, getWeekDay(tomorrow), kind, change);
            System.out.println(sql);
            ResultSet rs = stmt.executeQuery(sql);
            String s = "";
            while (rs.next()) {
                s += String.format("%s) %s \n", rs.getString("start"), rs.getString("discipline"));
            }
            if (s == "") {
                if (tomorrow) {
                    s = "Завтра выходной, отдыхайте!";
                } else {
                    s = "Сегодня выходной, отдыхайте!";
                }
            }
            sendMsg(sendMessage, s);
            stmt.close();
        } catch (SQLException ex) {
            System.out.println(ex.getMessage()+" "+sql);
        } finally {
        }
        disconnect();
    }

    public int getWeekDay(boolean tomorrow) {
        java.util.Date date = new java.util.Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int day = cal.get(Calendar.DAY_OF_WEEK);
        if (tomorrow) {
            return day;
        } else {
            return ((day == 1) ? 7 : --day);
        }
    }

    int getIsChangeDay(boolean tomorrow) {
        java.util.Date date = new java.util.Date();
        Calendar c = Calendar.getInstance();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        c.set(2017, 9, 27);
        connect();
        try{
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            String sql = String.format("SELECT \"dateChange\" FROM T_ADMINISTRATOR");
            ResultSet rs = stmt.executeQuery(sql);
            rs.next();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            try{
                c.setTime(sdf.parse(rs.getString("dateChange")));// all done
            }
            catch(ParseException e){
            System.out.println(e.toString());}
        }
        catch(SQLException ex) {
            System.out.println(ex.toString());}
            
        if (tomorrow) {
            c.add(Calendar.DATE, 1);
        }
        if (cal.after(c)) {
            return 2;
        } else {
            return 1;
        }
    }

    int getWeekNum(boolean tomorrow) {
        GregorianCalendar gc = new GregorianCalendar();
        int day = 1;
        if (!tomorrow) {
            day = 0;
        }
        gc.add(Calendar.DATE, day);
        return gc.get(Calendar.WEEK_OF_YEAR);
    }

    private enum state {
        faculty, team, course, day
    }
}
