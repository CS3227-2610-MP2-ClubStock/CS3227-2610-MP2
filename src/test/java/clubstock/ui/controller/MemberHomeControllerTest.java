package clubstock.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemberHomeControllerTest {
    @Test
    void formatAvailableQuantity_keepsZeroStockVisibleAsText() {
        assertEquals("0 available", MemberHomeController.formatAvailableQuantity(0));
    }

    @Test
    void formatAvailableQuantity_displaysPositiveQuantity() {
        assertEquals("2 available", MemberHomeController.formatAvailableQuantity(2));
    }
}
