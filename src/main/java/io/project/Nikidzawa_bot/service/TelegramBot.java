package io.project.Nikidzawa_bot.service;

import com.vdurmont.emoji.EmojiParser;
import io.project.Nikidzawa_bot.config.BotConfiguration;
import io.project.Nikidzawa_bot.store.entities.UserEntity;
import io.project.Nikidzawa_bot.store.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
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
    private HashMap<Long, List<Pair<UserEntity, String>>> peopleWhoLiked = new HashMap<>();
    private HashMap<Long, List<UserEntity>>recomendationUsers = new HashMap<>();
    private HashMap<Long, UserEntity> intermediateCache = new HashMap<>();
    @SneakyThrows
    @Override
    @Transactional
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
                        sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
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
                    } else sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                    break;
                case ASK_GENDER_SEARCH:
                    if (messageText.equals("Мужчина") || messageText.equals("Женщина")) {
                        List<String> genderSearch = Arrays.asList("Мужчины", "Женщины", "Без разницы");
                        ReplyKeyboardMarkup choseGenderMarkUp = KeyboardMarkupBuilder(genderSearch);

                        sendMessage(userId, message.ASK_GENDER_SEARCH, choseGenderMarkUp);
                        userEntity.setGender(messageText);
                        userEntity.setState_enum(EnumCurrentState.ASK_AGE.name());
                    } else sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                    break;
                case ASK_AGE:
                    if (messageText.equals("Мужчины") || messageText.equals("Женщины") || messageText.equals("Без разницы")) {
                        userEntity.setGenderSearch(messageText);
                        if (userEntity.getAge() != 0) {
                            List<String> setMyAgeRows = List.of(String.valueOf(userEntity.getAge()));
                            ReplyKeyboardMarkup setMyAge = KeyboardMarkupBuilder(setMyAgeRows);
                            sendMessage(userId, message.ASK_AGE, setMyAge);
                        } else {
                            sendMessage(userId, message.ASK_AGE);
                        }
                        userEntity.setState_enum(EnumCurrentState.ASK_CITY.name());
                    } else sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                    break;
                case ASK_CITY:
                    if (messageText.matches("\\d+") && Integer.parseInt(messageText) >= 12 && Integer.parseInt(messageText) <= 100) {
                        if (userEntity.getCity() != null) {
                            List<String> setMyCityRows = List.of(userEntity.getCity());
                            ReplyKeyboardMarkup setMyCity = KeyboardMarkupBuilder(setMyCityRows);
                            sendMessage(userId, message.ASK_CITY, setMyCity);
                        } else {
                            sendMessage(userId, message.ASK_CITY);
                        }
                        userEntity.setAge(Integer.parseInt(messageText));
                        userEntity.setState_enum(EnumCurrentState.ASK_INFO.name());
                    } else sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                    break;
                case ASK_INFO:
                    if (userEntity.getInfo() != null) {
                        List<String> setMyInfoRows = List.of("Оставить текущий текст");
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
                    } else {sendMessage(userId, message.ASK_PHOTO);}

                    if (!messageText.equals("Оставить текущий текст")) {
                        userEntity.setInfo(messageText);
                    }
                    userEntity.setState_enum(EnumCurrentState.ASK_RESULT.name());
                    break;
                case ASK_RESULT:
                    List<String> resultAnswerRows = Arrays.asList("Заполнить заново", "Продолжить");
                    ReplyKeyboardMarkup resultAnswer = KeyboardMarkupBuilder(resultAnswerRows);
                    if (update.getMessage().hasPhoto()) {
                        userEntity.setPhoto(loadPhoto(update));
                        sendMessage(userId, "Ваша анкета: ");
                        sendDatingSiteProfile(userId, userEntity);
                        sendMessage(userId, "Всё верно?", resultAnswer);

                    } else if (messageText.equals("Оставить текущее фото")) {
                        sendMessage(userId, "Ваша анкета: ");
                        sendDatingSiteProfile(userId, userEntity);
                        sendMessage(userId, "Всё верно?", resultAnswer);
                    } else if (messageText.equals("Заполнить заново")) {
                        userEntity.setState_enum(EnumCurrentState.ASK_GENDER.name());
                        sendMessage(userId, message.ASK_NAME);
                        break;
                    } else if (messageText.equals("Продолжить")) {
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNotAndIsActiveEquals(userEntity.getCity(), userId, true));
                        sendMessageNotRemoveMarkup(userId, EmojiParser.parseToUnicode(":mag::sparkles:"));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                        userEntity.setIsActive(true);
                        break;
                    } else {
                        sendMessage(userId, message.EXCEPTION, resultAnswer);
                    }
            }
            //Просмтр анент, Отслеживание лайков
            switch (currentState) {
                case FIND_PEOPLE:
                    if (messageText.equals(EmojiParser.parseToUnicode(":heart:"))) {
                        Long peopleId = intermediateCache.get(userId).getId();
                        List<Pair<UserEntity, String>> likes = peopleWhoLiked.getOrDefault(peopleId, new ArrayList<>());
                        likes.add(Pair.of(userEntity, null));
                        peopleWhoLiked.put(peopleId, likes);
                        sendMessageNotRemoveMarkup(userId, "Лайк отправлен");

                        List<String> userReactionRows = Arrays.asList("Показать", "Посмотреть позже");
                        ReplyKeyboardMarkup userReactionMarkup = KeyboardMarkupBuilder(userReactionRows);
                        if (intermediateCache.get(userId).getState_enum().equals(EnumCurrentState.FIND_PEOPLE.name())
                                && intermediateCache.get(userId).getGender().equals("Женщина")) {
                            sendMessageNotRemoveMarkup(intermediateCache.get(userId).getId(),
                                    "Заканчивай с просмотром анкет, ты кому-то понравилась!");
                        } else if (intermediateCache.get(userId).getState_enum().equals(EnumCurrentState.FIND_PEOPLE.name())
                                && intermediateCache.get(userId).getGender().equals("Мужчина")) {
                            sendMessageNotRemoveMarkup(intermediateCache.get(userId).getId(),
                                    "Заканчивай с просмотром анкет, ты кому-то понравился!");
                        } else {
                            sendMessage(intermediateCache.get(userId).getId(),
                                    "Твоя анкета кому-то понравилась!", userReactionMarkup);
                            intermediateCache.get(userId).setState_enum(EnumCurrentState.VIEW_OR_NOT_WHO_LIKED_ME.name());
                            userRepository.save(intermediateCache.get(userId));
                        }
                        intermediateCache.remove(userId);
                    }
                    else if (messageText.equals(EmojiParser.parseToUnicode(":zzz:"))) {
                        recomendationUsers.remove(userId);
                        intermediateCache.remove(userId);
                        mainMenuCurrentStateMessage(userEntity);
                        break;
                    }
                    else if (messageText.equals(EmojiParser.parseToUnicode(":love_letter::movie_camera:"))) {
                       List<String> backToFindPeopleRow = List.of("Вернуться назад");
                       ReplyKeyboardMarkup backToFindPeopleMarkup = KeyboardMarkupBuilder(backToFindPeopleRow);
                        sendMessage(userId, "Введи сообщение которое хочешь отправить", backToFindPeopleMarkup);
                        userEntity.setState_enum(EnumCurrentState.SEND_LOVE_LETTER.name());
                        userRepository.save(userEntity);
                        break;
                    }
                    findPeopleAndPutInCache(userEntity);
                    break;
                case SEND_LOVE_LETTER:
                    Long peopleId = intermediateCache.get(userId).getId();
                    List<Pair<UserEntity, String>> likes = peopleWhoLiked.getOrDefault(peopleId, new ArrayList<>());

                    likes.add(Pair.of(userEntity, messageText));
                    peopleWhoLiked.put(peopleId, likes);

                    sendMessageNotRemoveMarkup(userId, "Сообщение отправлено");
                    backToSecondMenu(userEntity);
                    userRepository.save(userEntity);
                    break;
            }
            //Главное меню
            switch (currentState) {
                case MAIN_MENU_ENTER:
                    if (messageText.equals("Вернуться в меню")) {
                        mainMenuCurrentStateMessage(userEntity);
                    }
                    else if (messageText.equals("Продолжить просмотр анкет")) {
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNotAndIsActiveEquals(userEntity.getCity(), userId, true));
                        sendMessageNotRemoveMarkup(userId, EmojiParser.parseToUnicode(":mag::sparkles:"));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                    }
                    else {
                        sendMessageNotRemoveMarkup(userId, message.EXCEPTION);}
                    break;
                case MAIN_MENU:
                    if (EmojiParser.parseToUnicode(messageText).equals
                            (EmojiParser.parseToUnicode("1" + ":rocket:"))) {
                        if (peopleWhoLiked.containsKey(userId) && peopleWhoLiked.get(userId).size() > 0) {
                            showPeopleWhoLikedMe(userEntity);
                            userEntity.setState_enum(EnumCurrentState.SHOW_WHO_LIKED_ME.name());
                            userRepository.save(userEntity);
                            break;
                        }
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNotAndIsActiveEquals(
                                userEntity.getCity(), userId, true));
                        sendMessageNotRemoveMarkup(userId,
                                EmojiParser.parseToUnicode(":mag::sparkles:"));
                        userEntity.setState_enum(EnumCurrentState.FIND_PEOPLE.name());
                        findPeopleAndPutInCache(userEntity);
                        break;
                    }
                    switch (messageText) {
                        case "2":
                            backToSecondMenu(userEntity);
                            break;
                        case "3":
                            sendMessage(userId, "Ваша анкета отключена. Надеюсь, ты смог найти себе кого-нибудь!");
                            userEntity.setState_enum(EnumCurrentState.WAITING_TO_ACTIVATE.name());
                            userEntity.setIsActive(true);
                            userRepository.save(userEntity);
                            break;
                        default: sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                    }
                    break;
                // TODO: 26.10.2023 при поиске анкет из second menu игнорируются анкеты лайкнувших
                case SECOND_MENU:
                    if (EmojiParser.parseToUnicode(messageText).equals(
                            EmojiParser.parseToUnicode("4:rocket:"))) {
                        sendMessageNotRemoveMarkup(userId,
                                EmojiParser.parseToUnicode(":mag::sparkles:"));
                        recomendationUsers.put(userId, userRepository.findAllByCityAndIdNotAndIsActiveEquals(
                                userEntity.getCity(), userId, true));
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
                                List<String> backToMenuRow = List.of("Вернуться назад");
                                ReplyKeyboardMarkup backToMenuMarkup = KeyboardMarkupBuilder(backToMenuRow);
                                sendMessage(userId, message.ASK_PHOTO_CHANGE, backToMenuMarkup);
                                userEntity.setState_enum(EnumCurrentState.ASK_PHOTO_CHANGE.name());
                                break;
                            case "3":
                                List<String> backToMenuRow2 = List.of("Вернуться назад");
                                ReplyKeyboardMarkup backToMenuMarkup2 = KeyboardMarkupBuilder(backToMenuRow2);
                                sendMessage(userId, message.ASK_INFO_CHANGE, backToMenuMarkup2);
                                userEntity.setState_enum(EnumCurrentState.ASK_INFO_CHANGE.name());
                                break;
                            default: sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
                        }
                    }
            }
            //Изменение профиля из состояния SECOND_MENU
            switch (currentState) {
                case ASK_PHOTO_CHANGE:
                    if (update.getMessage().hasPhoto()) {
                        userEntity.setPhoto(loadPhoto(update));
                        sendMessage(userId, "Давай посмотрим как теперь выглядит твоя анкета");
                        backToSecondMenu(userEntity);
                    } else if (messageText.equals("Вернуться назад")) {
                        sendMessage(userId, "Возвращаемся назад");
                        backToSecondMenu(userEntity);
                    } else {
                        sendMessageNotRemoveMarkup(userId, message.EXCEPTION);
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
            //Ответ на лайк
            switch (currentState) {
                case VIEW_OR_NOT_WHO_LIKED_ME:
                    switch (messageText) {
                        case "Показать" :
                            userEntity.setState_enum(EnumCurrentState.SHOW_WHO_LIKED_ME.name());
                            userRepository.save(userEntity);
                            showPeopleWhoLikedMe(userEntity);
                            break;
                        case "Посмотреть позже" :
                            mainMenuCurrentStateMessage(userEntity);
                            break;
                        default:
                            sendMessageNotRemoveMarkup(userId, message.EXCEPTION);}
                    break;
                case SHOW_WHO_LIKED_ME:
                    if (EmojiParser.parseToUnicode(messageText)
                            .equals(EmojiParser.parseToUnicode(":heart:"))) {
                        GetChat getChatRequest = new GetChat();
                        getChatRequest.setChatId(intermediateCache.get(userId).getId());
                        Chat chat = execute(getChatRequest);

                        sendDatingSiteProfile(intermediateCache.get(userId).getId(), userEntity);
                        sendMessageNotRemoveMarkup(intermediateCache.get(userId).getId(), "Есть взаимная симпания!\n" +
                                "https://t.me/" + update.getMessage().getChat().getUserName() + "\nЖелаем вам хорошо провести время!");
                        sendMessageNotRemoveMarkup(userId, "https://t.me/" + chat.getUserName() + "\nЖелаем вам хорошо провести время!");
                        intermediateCache.remove(userId);
                    }
                    else if (EmojiParser.parseToUnicode(messageText)
                            .equals(EmojiParser.parseToUnicode(":zzz:"))) {
                        mainMenuCurrentStateMessage(userEntity);
                        break;
                    }
                    if (peopleWhoLiked.containsKey(userId) && !peopleWhoLiked.get(userId)
                            .isEmpty()) {
                        showPeopleWhoLikedMe(userEntity);
                    }
                    else {
                        List<String> returnMainMenuOrFindUserRows = List.of("Продолжить просмотр анкет", "Вернуться в меню");
                        ReplyKeyboardMarkup returnMainMenuOrFindUser = KeyboardMarkupBuilder(returnMainMenuOrFindUserRows);
                        sendMessage(userId, "На этом всё, продолжить просмотр анкет?", returnMainMenuOrFindUser);
                        userEntity.setState_enum(EnumCurrentState.MAIN_MENU_ENTER.name());
                    }
            }
            //Ожидание активации анкеты
            switch (currentState) {
                case WAITING_TO_ACTIVATE :
                    if (messageText.equals("Включить анкету")) {
                        userEntity.setIsActive(true);
                        backToSecondMenu(userEntity);
                        userRepository.save(userEntity);
                        break;
                    }
                    List<String> waitingToActivateRows = List.of("Включить анкету");
                    ReplyKeyboardMarkup waitingToActivateMarkup = KeyboardMarkupBuilder(waitingToActivateRows);
                    sendMessage(userId, "Мы тебя помним! Хочешь снова включить анкету?",  waitingToActivateMarkup);
                    break;
            }
        }
        userRepository.save(userEntity);
    }
    @SneakyThrows
    private void showPeopleWhoLikedMe (UserEntity userEntity) {
        Long userId = userEntity.getId();
        List<String> strings = Arrays.asList(
                EmojiParser.parseToUnicode(":heart:"),
                EmojiParser.parseToUnicode(":thumbsdown:"),
                EmojiParser.parseToUnicode(":zzz:")
        );
        sendMessageNotRemoveMarkup(userId, "Ты кому-то понравился!");
        ReplyKeyboardMarkup digit = KeyboardMarkupBuilder(strings);
        List<Pair<UserEntity, String>> users = peopleWhoLiked.get(userId);
        Optional<Pair<UserEntity, String>> isFirstLikedMeUser = users.stream().findFirst();
        isFirstLikedMeUser.ifPresentOrElse(likedMeUserPair -> {
            UserEntity likedUserEntity = likedMeUserPair.getLeft();
            String message = likedMeUserPair.getRight();
            if (message != null) {
                sendDatingSiteProfile(userId, likedUserEntity, digit, message);
            } else {
                sendDatingSiteProfile(userId, likedUserEntity, digit);
            }
            intermediateCache.put(userId, likedUserEntity);
            users.remove(likedMeUserPair);

            if (users.isEmpty()) {
            peopleWhoLiked.remove(userId);}
        }, () -> {
            peopleWhoLiked.remove(userId);
            List<String> returnMainMenuOrFindUserRows = List.of("Продолжить просмотр анкет", "Вернуться в меню");
            ReplyKeyboardMarkup returnMainMenuOrFindUser = KeyboardMarkupBuilder(returnMainMenuOrFindUserRows);
            try {
                sendMessage(userId, "На этом всё, продолжить просмотр анкет?", returnMainMenuOrFindUser);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            userEntity.setState_enum(EnumCurrentState.MAIN_MENU_ENTER.name());
            userRepository.save(userEntity);
        });
    }
    private String loadPhoto (Update update) {
        List<PhotoSize> photos = update.getMessage().getPhoto();
        String fileId = photos.stream()
                .sorted(Comparator.comparing(PhotoSize::getFileSize).reversed())
                .findFirst()
                .orElse(null).getFileId();
        return fileId;
    }
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
            userEntity.setState_enum(EnumCurrentState.MAIN_MENU_ENTER.name());
        });
    }
    @SneakyThrows
    private void mainMenuCurrentStateMessage(UserEntity userEntity) {
        Long userId = userEntity.getId();
        userEntity.setState_enum(EnumCurrentState.MAIN_MENU.name());
        if (peopleWhoLiked.get(userId) == null || peopleWhoLiked.get(userId).isEmpty())
        {sendMessage(userId, "Подождём пока кто-то увидит твою анкету");}
        else
        {sendMessage(userId, "Ты понравился " +
                peopleWhoLiked.get(userId).size() + " людям. Показать их?");}
        List<String> mainMenuStringRows = Arrays.asList(
                EmojiParser.parseToUnicode("1" + ":rocket:"), "2", "3");
        ReplyKeyboardMarkup mainMenuMarkup = KeyboardMarkupBuilder(mainMenuStringRows);
        sendMessage(userId, message.MAIN_MENU, mainMenuMarkup);
        userRepository.save(userEntity);
    }
    @SneakyThrows
    private void backToSecondMenu(UserEntity userEntity) {
        Long userId = userEntity.getId();
        List<String> secondMenuRows = Arrays.asList
                ("1", "2", "3", EmojiParser.parseToUnicode("4:rocket:"));
        sendMessage(userId, "Так выглядит твоя анкета: ");
        ReplyKeyboardMarkup secondMenuMarkup = KeyboardMarkupBuilder(secondMenuRows);
        sendDatingSiteProfile(userId, userEntity, secondMenuMarkup);
        sendMessageNotRemoveMarkup(userId, message.SECOND_MENU);
        userEntity.setState_enum(EnumCurrentState.SECOND_MENU.name());
        userRepository.save(userEntity);
    }
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
    private void sendDatingSiteProfile (Long userId, UserEntity userEntity, ReplyKeyboardMarkup markup, String message) {
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
                + userEntity.getInfo() + "\n\n"
                + EmojiParser.parseToUnicode(":love_letter:")
                + "сообщение для тебя: "
                + message);
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
    private void sendMessageNotRemoveMarkup(Long id, String message) throws TelegramApiException {
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