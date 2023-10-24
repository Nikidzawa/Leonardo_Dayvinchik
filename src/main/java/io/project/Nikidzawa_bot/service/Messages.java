package io.project.Nikidzawa_bot.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class Messages {
    @Value(value = "${ask.start}")
    String START;
    @Value(value = "${ask.name}")
    String ASK_NAME;
    @Value(value = "${ask.gender}")
    String ASK_GENDER;
    @Value(value = "${ask.gender.search}")
    String ASK_GENDER_SEARCH;
    @Value(value = "${ask.age}")
    String ASK_AGE;
    @Value(value = "${ask.city}")
    String ASK_CITY;
    @Value(value = "${ask.info}")
    String ASK_INFO;
    @Value(value = "${ask.result}")
    String ASK_RESULT;
    @Value(value = "${ask.photo}")
    String ASK_PHOTO;
    @Value(value = "${find.people}")
    String FIND_PEOPLE;
    @Value(value ="${main.menu}")
    String MAIN_MENU;
    @Value(value = "${failed_search}")
    String FAILED_SEARCH;
    @Value(value = "${second.menu}")
    String SECOND_MENU;
    @Value(value = "${ask.photo.change}")
    String ASK_PHOTO_CHANGE;
    @Value(value = "${ask.info.change}")
    String ASK_INFO_CHANGE;
    //---------------EXCEPTIONS---------------//
    @Value(value = "${exception}")
    String EXCEPTION;
}
