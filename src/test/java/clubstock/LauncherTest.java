package clubstock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LauncherTest {
    @Test
    void verificationMode_preservesNormalArguments() {
        assertEquals(0, Launcher.verificationMode(new String[0]));
        assertEquals(0, Launcher.verificationMode(new String[]{"normal-argument"}));
    }

    @Test
    void verificationMode_acceptsOnlyStandaloneFlag() {
        assertEquals(1, Launcher.verificationMode(new String[]{"--verify-install"}));
        assertEquals(2, Launcher.verificationMode(new String[]{"--verify-install", "extra"}));
        assertEquals(2, Launcher.verificationMode(new String[]{"extra", "--verify-install"}));
        assertEquals(2, Launcher.verificationMode(new String[]{"--verify-install", "--verify-install"}));
    }
}
