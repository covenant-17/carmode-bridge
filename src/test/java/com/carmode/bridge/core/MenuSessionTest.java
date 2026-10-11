package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSessionTest {
    @Test void singlePressSelectsCameraAfterExactlyTwoSeconds() {
        MenuSession menu = new MenuSession(); menu.press(100);
        assertEquals(0, menu.selected()); assertEquals(-1, menu.poll(2099));
        assertEquals(0, menu.poll(2100)); assertFalse(menu.isOpen());
    }
    @Test void secondPressRestartsDeadlineAndSelectsMusic() {
        MenuSession menu = new MenuSession(); menu.press(0); menu.press(1900);
        assertEquals(-1, menu.poll(2000)); assertEquals(-1, menu.poll(3899));
        assertEquals(1, menu.poll(3900));
    }
    @Test void thirdPressSelectsMaps() {
        MenuSession menu = new MenuSession(); menu.press(0); menu.press(10); menu.press(20);
        assertEquals(2, menu.poll(2020));
    }
    @Test void fourthPressWrapsToCamera() {
        MenuSession menu = new MenuSession(); for (int i = 0; i < 4; i++) menu.press(i * 10);
        assertEquals(0, menu.poll(2030));
    }
    @Test void consumedSelectionCannotExecuteTwice() {
        MenuSession menu = new MenuSession(); menu.press(0);
        assertEquals(0, menu.poll(2000)); assertEquals(-1, menu.poll(2000)); assertEquals(-1, menu.poll(9999));
    }
    @Test void cancelledTimerCannotLaunch() {
        MenuSession menu = new MenuSession(); menu.press(0); menu.cancel();
        assertEquals(-1, menu.poll(2000)); assertEquals(0, menu.remaining(2000));
    }
    @Test void everyNewOpeningResetsSelection() {
        MenuSession menu = new MenuSession(); menu.press(0); menu.press(1); menu.poll(2001);
        menu.press(3000); assertEquals(0, menu.selected());
        menu.press(3001); menu.cancel(); menu.press(3002); assertEquals(0, menu.selected());
    }
    @Test void progressShrinksAndResets() {
        MenuSession menu = new MenuSession(); menu.press(0);
        assertEquals(1f, menu.remaining(0)); assertEquals(.5f, menu.remaining(1000));
        menu.press(1000); assertEquals(1f, menu.remaining(1000)); assertEquals(0f, menu.remaining(4000));
    }
    @Test void staleWakeupObservesLatestDeadline() {
        MenuSession menu = new MenuSession(); menu.press(0); menu.press(1999);
        assertEquals(-1, menu.poll(2000)); assertEquals(1, menu.poll(3999));
    }
    @Test void supportsLongUptime() {
        MenuSession menu = new MenuSession(); long now = Integer.MAX_VALUE + 10000L; menu.press(now);
        assertEquals(0, menu.poll(now + 2000));
    }
}
