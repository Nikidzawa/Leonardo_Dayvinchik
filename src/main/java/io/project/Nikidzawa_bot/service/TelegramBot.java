package io.project.Nikidzawa_bot.service;

import com.vdurmont.emoji.EmojiParser;
import io.project.Nikidzawa_bot.config.BotConfiguration;
import io.project.Nikidzawa_bot.store.entities.UserEntity;
import io.project.Nikidzawa_bot.store.repositories.UserRepository;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    UserRepository userRepository;

    @Autowired
    Messages message;

    BotConfiguration config;

    public TelegramBot(BotConfiguration config) throws TelegramApiException {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Запуск бота"));
        listOfCommands.add(new BotCommand("/my_profile", "ваша анкета"));
        listOfCommands.add(new BotCommand("/help", "Информация о командах"));

        this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        boolean isMessage = update.hasMessage();
        Long userId = update.getMessage().getChatId();
        UserEntity userEntity = userRepository.findById(userId).orElseGet(() -> {
            UserEntity newUser = new UserEntity();
            newUser.setId(userId);
            newUser.setState_enum(EnumCurrentState.START.name());
            userRepository.save(newUser);
            return newUser;
        });

        if (isMessage) {
            String messageText = update.getMessage().getText();
            EnumCurrentState currentState = EnumCurrentState.valueOf(userEntity.getState_enum());
            //Регистрация или повторное заполнение всей анкеты
            switch (currentState) {
                case START:
                    List<String> startMessage = List.of("Начнём!");
                    ReplyKeyboardMarkup startMessageMarkUp = KeyboardMarkupBuilder(startMessage);
                    sendMessage(userId, message.getSTART(), startMessageMarkUp);
                    userEntity.setState_enum(EnumCurrentState.ASK_NAME.name());
                    break;
                case ASK_NAME:
                    if (messageText.equals("Начнём!")) {
                        sendMessage(userId, message.ASK_NAME);
                    } else {
                        sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    }
                    userEntity.setState_enum(EnumCurrentState.ASK_GENDER.name());
                    break;
                case ASK_GENDER:
                    if (messageText.length() <= 20) {
                        List<String> userGender = Arrays.asList("Мужчина", "Женщина");
                        ReplyKeyboardMarkup userGenderMurkUp = KeyboardMarkupBuilder(userGender);

                        sendMessage(userId, message.ASK_GENDER, userGenderMurkUp);
                        userEntity.setName(messageText);
                        userEntity.setState_enum(EnumCurrentState.ASK_GENDER_SEARCH.name());
                    } else sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    break;
                case ASK_GENDER_SEARCH:
                    if (messageText.equals("Мужчина") || messageText.equals("Женщина")) {
                        List<String> genderSearch = Arrays.asList("Мужчины", "Женщины", "Без разницы");
                        ReplyKeyboardMarkup choseGenderMarkUp = KeyboardMarkupBuilder(genderSearch);

                        sendMessage(userId, message.ASK_GENDER_SEARCH, choseGenderMarkUp);
                        userEntity.setGender(messageText);
                        userEntity.setState_enum(EnumCurrentState.ASK_AGE.name());
                    } else sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    break;
                case ASK_AGE:
                    if (messageText.equals("Мужчины") || messageText.equals("Женщины") || messageText.equals("Без разницы")) {
                        userEntity.setGenderSearch(messageText);
                        if (userEntity.getAge() != 0) {
                            List<String> setMyAgeRows = Arrays.asList(String.valueOf(userEntity.getAge()));
                            ReplyKeyboardMarkup setMyAge = KeyboardMarkupBuilder(setMyAgeRows);
                            sendMessage(userId, message.ASK_AGE, setMyAge);
                        } else {
                            sendMessage(userId, message.ASK_AGE);
                        }
                        userEntity.setState_enum(EnumCurrentState.ASK_CITY.name());
                    } else sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    break;
                case ASK_CITY:
                    if (messageText.matches("\\d+") && Integer.parseInt(messageText) >= 12 && Integer.parseInt(messageText) <= 100) {
                        if (userEntity.getCity() != null) {
                            List<String> setMyCityRows = Arrays.asList(userEntity.getCity());
                            ReplyKeyboardMarkup setMyCity = KeyboardMarkupBuilder(setMyCityRows);
                            sendMessage(userId, message.ASK_CITY, setMyCity);
                        } else {
                            sendMessage(userId, message.ASK_CITY);
                        }
                        userEntity.setAge(Integer.parseInt(messageText));
                        userEntity.setState_enum(EnumCurrentState.ASK_INFO.name());
                    } else sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    break;
                case ASK_INFO:
                    if (userEntity.getInfo() != null) {
                        List<String> setMyInfoRows = Arrays.asList("Оставить текущий текст");
                        ReplyKeyboardMarkup setMyCityMarkup = KeyboardMarkupBuilder(setMyInfoRows);
                        sendMessage(userId, message.ASK_INFO, setMyCityMarkup);
                    } else {
                        sendMessage(userId, message.ASK_INFO);
                    }
                    userEntity.setCity(messageText);
                    userEntity.setState_enum(EnumCurrentState.ASK_PHOTO.name());
                    break;
                case ASK_PHOTO:
                    if (userEntity.getPhoto() != null) {
                        List<String> setMyPhotoRows = List.of("Оставить текущее фото");
                        ReplyKeyboardMarkup setMyPhoto = KeyboardMarkupBuilder(setMyPhotoRows);
                        sendMessage(userId, message.ASK_PHOTO, setMyPhoto);
                    } else {
                        sendMessage(userId, message.ASK_PHOTO);
                    }
                    if (!messageText.equals("Оставить текущий текст")) {
                        userEntity.setInfo(messageText);
                    }
                    userEntity.setState_enum(EnumCurrentState.ASK_RESULT.name());
                    break;
                case ASK_RESULT:
                    List<String> resultAnswerRows = Arrays.asList("Заполнить заново", "Продолжить");
                    ReplyKeyboardMarkup resultAnswer = KeyboardMarkupBuilder(resultAnswerRows);
                    if (update.getMessage().hasPhoto()) {
                        List<PhotoSize> photos = update.getMessage().getPhoto();
                        String fileId = photos.stream()
                                .sorted(Comparator.comparing(PhotoSize::getFileSize).reversed())
                                .findFirst()
                                .orElse(null).getFileId();
                        userEntity.setPhoto(fileId);
                        sendMessage(userId, "Ваша анкета: ");
                        sendDatingSiteProfile(userId, userEntity);
                        sendMessage(userId, "Всё верно?", resultAnswer);

                    } else if (messageText.equals("Оставить текущее фото")) {
                        sendMessage(userId, "Ваша анкета: ");
                        sendDatingSiteProfile(userId, userEntity);
                        sendMessage(userId, "Всё верно?", resultAnswer);
                    } else if (messageText.equals("Заполнить заново")) {
                        userEntity.setState_enum(EnumCurrentState.ASK_NAME.name());
                        break;
                    } else if (messageText.equals("Продолжить")) {
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNot(userEntity.getCity(), userId));
                        sendMessageNotRemoveMarkUp(userId, EmojiParser.parseToUnicode(":mag::sparkles:"));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                        break;
                    } else {
                        sendMessage(userId, message.EXCEPTION, resultAnswer);
                    }
            }
            //Просмтр анент, Отслеживание лайков
            switch (currentState) {
                case FIND_PEOPLE:
                    if (messageText.equals(EmojiParser.parseToUnicode(":heart:"))) {
                        peopleWhoLiked.put(intermediateCache.get(userId).getId(), Arrays.asList(userEntity));
                        sendMessageNotRemoveMarkUp(userId, "Лайк отправлен");
                        if (intermediateCache.get(userId).getState_enum().equals(EnumCurrentState.FIND_PEOPLE.name())
                                && intermediateCache.get(userId).getGender().equals("Женщина")) {
                            sendMessageNotRemoveMarkUp(intermediateCache.get(userId).getId(),
                                    "Заканчивай с просмотром анкет, ты кому-то понравилась!");
                        } else if (intermediateCache.get(userId).getState_enum().equals(EnumCurrentState.FIND_PEOPLE.name())
                                && intermediateCache.get(userId).getGender().equals("Мужчина")) {
                            sendMessageNotRemoveMarkUp(intermediateCache.get(userId).getId(),
                                    "Заканчивай с просмотром анкет, ты кому-то понравился!");
                        } else {
                            sendMessageNotRemoveMarkUp(intermediateCache.get(userId).getId(),
                                    "Твоя анкета кому-то понравилась!");
                        }
                        intermediateCache.remove(userId);
                    } else if (messageText.equals(EmojiParser.parseToUnicode(":zzz:"))) {
                        recomendationUsers.remove(userId);
                        intermediateCache.remove(userId);
                        userEntity.setState_enum(EnumCurrentState.MAIN_MENU.name());
                        mainMenuCurrentStateMessage(userId);
                        break;
                    } else if (messageText.equals(EmojiParser.parseToUnicode(":love_letter:"))) {

                        break;
                    }
                    findPeopleAndPutInCache(userEntity);
                    break;
            }
            //Главное меню
            switch (currentState) {
                case MAIN_MENU:
                    if (EmojiParser.parseToUnicode(messageText).equals
                            (EmojiParser.parseToUnicode("1" + ":rocket:"))) {
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNot(
                                userEntity.getCity(), userId));
                        sendMessageNotRemoveMarkUp(userId,
                                EmojiParser.parseToUnicode(":mag::sparkles:"));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                        break;
                    }
                    else if (messageText.equals("2")) {
                        backToSecondMenu(userEntity);
                        break;
                    }
                    else {
                        if (peopleWhoLiked.get(userId) == null)
                        {sendMessage(userId, "Подождём пока кто-то увидит твою анкету");}
                        else
                        {sendMessage(userId, "Ты понравился " +
                                    peopleWhoLiked.get(userId).size() + " людям. Показать их?");}
                        mainMenuCurrentStateMessage(userId);
                    }
                    break;
                case SECOND_MENU:
                    if (EmojiParser.parseToUnicode(messageText).equals(
                            EmojiParser.parseToUnicode("4:rocket:"))) {
                        sendMessageNotRemoveMarkUp(userId,
                                EmojiParser.parseToUnicode(":mag::sparkles:"));
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNot(
                                userEntity.getCity(), userId));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                    } else {
                        switch (messageText) {
                            case "1":
                                userEntity.setState_enum(EnumCurrentState.ASK_GENDER.name());
                                List<String> setNameRows = Arrays.asList(update.getMessage().getChat().getFirstName(), userEntity.getName());
                                ReplyKeyboardMarkup setMyNameMarkup = KeyboardMarkupBuilder(setNameRows);
                                sendMessage(userId, message.ASK_NAME, setMyNameMarkup);
                                break;
                            case "2":
                                List<String> backToMenuRow = Arrays.asList("Вернуться назад");
                                ReplyKeyboardMarkup backToMenuMarkup = KeyboardMarkupBuilder(backToMenuRow);
                                sendMessage(userId, message.ASK_PHOTO_CHANGE, backToMenuMarkup);
                                userEntity.setState_enum(EnumCurrentState.ASK_PHOTO_CHANGE.name());
                                break;
                            case "3":
                                List<String> backToMenuRow2 = Arrays.asList("Вернуться назад");
                                ReplyKeyboardMarkup backToMenuMarkup2 = KeyboardMarkupBuilder(backToMenuRow2);
                                sendMessage(userId, message.ASK_INFO_CHANGE, backToMenuMarkup2);
                                userEntity.setState_enum(EnumCurrentState.ASK_INFO_CHANGE.name());
                                break;
                            default:
                                sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                        }
                    }
            }
            //Изменение профиля из состояния SECOND_MENU
            switch (currentState) {
                case ASK_PHOTO_CHANGE:
                    if (update.getMessage().hasPhoto()) {
                        List<PhotoSize> photos = update.getMessage().getPhoto();
                        String fileId = photos.stream()
                                .sorted(Comparator.comparing(PhotoSize::getFileSize).reversed())
                                .findFirst()
                                .orElse(null).getFileId();
                        userEntity.setPhoto(fileId);
                        sendMessage(userId, "Давай посмотрим как теперь выглядит твоя анкета");
                        backToSecondMenu(userEntity);
                    } else if (messageText.equals("Вернуться назад")) {
                        sendMessage(userId, "Возвращаемся назад");
                        backToSecondMenu(userEntity);
                    } else {
                        sendMessageNotRemoveMarkUp(userId, message.EXCEPTION);
                    }
                    break;
                case ASK_INFO_CHANGE:
                    if (messageText.equals("Вернуться назад")) {
                        sendMessage(userId, "Возвращаемся назад");
                        backToSecondMenu(userEntity);
                    } else {
                        sendMessage(userId, "Давай посмотрим как теперь выглядит твоя анкета");
                        userEntity.setInfo(messageText);
                        backToSecondMenu(userEntity);
                    }
                    break;
            }
        }
        userRepository.save(userEntity);
    }
    //Ищет первого человека из кеша с рекомендациями, далее отправляет в человека промежуточный кеш
    private void findPeopleAndPutInCache (UserEntity userEntity) {
        Long userId = userEntity.getId();
        List<String> strings = Arrays.asList(
                EmojiParser.parseToUnicode(":heart:"),
                EmojiParser.parseToUnicode(":love_letter::movie_camera:"),
                EmojiParser.parseToUnicode(":thumbsdown:"),
                EmojiParser.parseToUnicode(":zzz:")
        );
        ReplyKeyboardMarkup digit = KeyboardMarkupBuilder(strings);
        List<UserEntity> userEntities = recomendationUsers.get(userId);
        Optional<UserEntity> isFirstUser = userEntities.stream().findFirst();
        isFirstUser.ifPresentOrElse(recomendUser -> {
            sendDatingSiteProfile(userId, recomendUser, digit);
            intermediateCache.put(userId, recomendUser);
            recomendationUsers.get(userId).remove(recomendUser);
        }, () -> {
            recomendationUsers.remove(userId);
            List<String> returnMainMenuText = List.of("Вернуться в меню");
            ReplyKeyboardMarkup keyboardMarkup = KeyboardMarkupBuilder(returnMainMenuText);
            try {
                sendMessage(userId, message.FAILED_SEARCH, keyboardMarkup);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            userEntity.setState_enum(EnumCurrentState.MAIN_MENU.name());
        });
    }
    @SneakyThrows
    private void mainMenuCurrentStateMessage(Long userId) {
        List<String> mainMenuStringRows = Arrays.asList(
                EmojiParser.parseToUnicode("1" + ":rocket:"), "2", "3");
        ReplyKeyboardMarkup mainMenuMarkup = KeyboardMarkupBuilder(mainMenuStringRows);
        sendMessage(userId, message.MAIN_MENU, mainMenuMarkup);
    }
    @SneakyThrows
    private void backToSecondMenu(UserEntity userEntity) {
        Long userId = userEntity.getId();
        List<String> secondMenuRows = Arrays.asList
                ("1", "2", "3", EmojiParser.parseToUnicode("4:rocket:"));
        sendMessage(userId, "Так выглядит твоя анкета: ");
        ReplyKeyboardMarkup secondMenuMarkup = KeyboardMarkupBuilder(secondMenuRows);
        sendDatingSiteProfile(userId, userEntity, secondMenuMarkup);
        sendMessageNotRemoveMarkUp(userId, message.SECOND_MENU);
        userEntity.setState_enum(EnumCurrentState.SECOND_MENU.name());
    }
    //Хранит в себе ключ - id человека которого лайкнули, значение - коллекция из сущностей лайкнувших человека
    private HashMap<Long, List<UserEntity>> peopleWhoLiked = new HashMap<>();
    //Хранит в себе ключ - id вызвавшего, значение - коллекция из сущностей (рекомендации анкет)
    private HashMap<Long, List<UserEntity>>recomendationUsers = new HashMap<>();
    // промежуточный кеш, который сохраняет анету в ожидании оценки, после оценки кеш чистится
    private HashMap<Long, UserEntity> intermediateCache = new HashMap<>();
    public ReplyKeyboardMarkup KeyboardMarkupBuilder(List<String> buttonLabels) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        for (String label : buttonLabels) {
           keyboardRow.add(label);
        }
        keyboardRows.add(keyboardRow);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        return replyKeyboardMarkup;
    }
    @SneakyThrows
    private void sendDatingSiteProfile (Long userId, UserEntity userEntity) {
        String fileId = userEntity.getPhoto();
        GetFile getFile = new GetFile(fileId);
        File file = execute(getFile);
        String filePath = file.getFilePath();
        URL fileUrl = new URL("https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath);
        InputStream inputStream = fileUrl.openStream();
        InputFile inputFile = new InputFile(inputStream, "фото.jpg");
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(userId);
        sendPhoto.setPhoto(inputFile);
        sendPhoto.setCaption(userEntity.getName() + ", "
                + userEntity.getAge() + ", "
                + userEntity.getCity() + "\n"
                + userEntity.getInfo());
        execute(sendPhoto);
    }
    @SneakyThrows
    private void sendDatingSiteProfile (Long userId, UserEntity userEntity, ReplyKeyboardMarkup replyKeyboardMarkup) {
        String fileId = userEntity.getPhoto();
        GetFile getFile = new GetFile(fileId);
        File file = execute(getFile);
        String filePath = file.getFilePath();
        URL fileUrl = new URL("https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath);
        InputStream inputStream = fileUrl.openStream();
        InputFile inputFile = new InputFile(inputStream, "фото.jpg");
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(userId);
        sendPhoto.setPhoto(inputFile);
        sendPhoto.setCaption(userEntity.getName() + ", "
                + userEntity.getAge() + ", "
                + userEntity.getCity() + "\n"
                + userEntity.getInfo());
        sendPhoto.setReplyMarkup(replyKeyboardMarkup);
        execute(sendPhoto);
    }
    private void sendMessageNotRemoveMarkUp(Long id, String message) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(id));
        sendMessage.setText(message);
        execute(sendMessage);
    }
    private void sendMessage (Long id, String message) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(id));
        sendMessage.setText(message);
        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove(true);
        sendMessage.setReplyMarkup(replyKeyboardRemove);
        execute(sendMessage);
    }
    private void sendMessage (Long id, String message, ReplyKeyboardMarkup markUp) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(id));
        sendMessage.setReplyMarkup(markUp);
        sendMessage.setText(message);
        execute(sendMessage);
    }
}