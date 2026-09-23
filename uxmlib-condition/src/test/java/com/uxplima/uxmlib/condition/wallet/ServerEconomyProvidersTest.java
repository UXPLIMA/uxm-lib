package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Access;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Answer;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Argument;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Calls;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Pools;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Where the object behind a binding comes from.
 *
 * <p>A binding that cannot find its object is a wallet that reads zero for the rest of the run, so the four
 * ways of finding that object are proved here: a service of the server, a chain of static methods, the
 * plugin itself, and a class whose methods are static.
 *
 * <p>The failure of each is proved as well, because every one of them has to be quiet. A plugin that is not
 * on the server, a class that is not in the jar and an accessor that answers with nothing are all ordinary,
 * and none of them may stop a server from starting.
 */
class ServerEconomyProvidersTest {

    private static final String SHAPES = FakeEconomies.class.getName();
    private static final System.Logger LOG = System.getLogger(ServerEconomyProvidersTest.class.getName());

    private ServerMock server;
    private Plugin economy;
    private EconomyProviders providers;

    @BeforeEach
    void startTheServer() {
        server = MockBukkit.mock();
        economy = MockBukkit.createMockPlugin("Money");
        providers = new ServerEconomyProviders(LOG);
    }

    @AfterEach
    void stopTheServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a service of the server is the object the calls go to")
    void aserviceOfTheServerIsTheObject() {
        FakeEconomies.VaultShaped registered = new FakeEconomies.VaultShaped(100);
        server.getServicesManager()
                .register(FakeEconomies.VaultShaped.class, registered, economy, ServicePriority.Normal);

        assertThat(providers.provider(binding(Access.SERVICE, null, SHAPES + "$VaultShaped")))
                .contains(registered);
    }

    @Test
    @DisplayName("a service nobody registered is nothing, and never a failure")
    void aserviceNobodyRegisteredIsNothing() {
        assertThat(providers.provider(binding(Access.SERVICE, null, SHAPES + "$VaultShaped")))
                .isEmpty();
    }

    @Test
    @DisplayName("an API behind two static hops is followed to the end")
    void anapiBehindTwoHopsIsFollowed() {
        Optional<Object> found = providers.provider(binding(Access.STATIC, "getInstance.getAPI", SHAPES + "$Hidden"));

        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(FakeEconomies.PointsShaped.class);
    }

    @Test
    @DisplayName("a static accessor that answers with nothing is nothing, and never a failure")
    void astaticAccessorOfNothingIsNothing() {
        assertThat(providers.provider(binding(Access.STATIC, "getInstance", SHAPES + "$NotStarted")))
                .isEmpty();
    }

    @Test
    @DisplayName("the plugin itself is the object, for an economy that answers on its own instance")
    void thepluginItselfIsTheObject() {
        assertThat(providers.provider(binding(Access.PLUGIN, null, SHAPES + "$VaultShaped")))
                .contains(economy);
    }

    @Test
    @DisplayName("a class whose methods are static is the object, and nothing is fetched first")
    void aclassOfStaticMethodsIsTheObject() {
        assertThat(providers.provider(binding(Access.CLASS, null, SHAPES + "$UtilityShaped")))
                .contains(FakeEconomies.UtilityShaped.class);
    }

    @Test
    @DisplayName("a plugin that is not on this server is asked nothing at all")
    void apluginThatIsNotHereIsAskedNothing() {
        EconomyBinding absent = new EconomyBinding(
                "NotHere",
                SHAPES + "$VaultShaped",
                Access.CLASS,
                null,
                "getBalance",
                "withdrawPlayer",
                Argument.OFFLINE_PLAYER,
                Answer.VAULT_RESPONSE,
                Pools.one(),
                Calls.simple());

        assertThat(providers.provider(absent)).isEmpty();
    }

    @Test
    @DisplayName("a plugin that is here without the class it names is a binding that stays off")
    void apluginWithoutItsClassStaysOff() {
        assertThat(providers.provider(binding(Access.CLASS, null, "com.example.money.NoSuchApi")))
                .isEmpty();
    }

    /**
     * Two generations of one plugin under one name. VaultUnlocked registers as Vault, so on a server with the
     * original Vault the VaultUnlocked binding finds its plugin and not its class. On 2026-09-23 that wrote a
     * warning and a whole stack trace, "The Vault economy is here but could not be reached", every time the
     * doctor of uxmJobs, uxmCrates or uxmSkills was run, on a server where nothing was wrong.
     */
    @Test
    @DisplayName(
            "a plugin that is here without the class it names says nothing, because that is a server's normal state")
    void apluginWithoutItsClassSaysNothing() {
        Recording log = new Recording();
        EconomyProviders quiet = new ServerEconomyProviders(log);

        assertThat(quiet.provider(binding(Access.SERVICE, null, "com.example.money.NoSuchApi")))
                .isEmpty();
        assertThat(quiet.service("Money", "com.example.money.NoSuchApi")).isEmpty();

        assertThat(log.warnings).isEmpty();
    }

    @Test
    @DisplayName("a class that is here and cannot be reached still warns, because that is a fault")
    void aclassThatCannotBeReachedWarns() {
        Recording log = new Recording();

        new ServerEconomyProviders(log).provider(binding(Access.STATIC, "noSuchAccessor", SHAPES + "$Hidden"));

        assertThat(log.warnings).hasSize(1);
    }

    @Test
    @DisplayName("a service asked for by name answers only when its plugin is here")
    void aserviceAskedForByNameNeedsItsPlugin() {
        FakeEconomies.VaultShaped registered = new FakeEconomies.VaultShaped(5);
        server.getServicesManager()
                .register(FakeEconomies.VaultShaped.class, registered, economy, ServicePriority.Normal);

        assertThat(providers.service("Money", SHAPES + "$VaultShaped")).contains(registered);
        assertThat(providers.service("NotHere", SHAPES + "$VaultShaped")).isEmpty();
        assertThat(providers.service("Money", "com.example.money.NoSuchApi")).isEmpty();
    }

    @Test
    @DisplayName("a plugin that is here is here, and one that is not is not")
    void apluginThatIsHereIsHere() {
        assertThat(ServerEconomyProviders.isPresent("Money")).isTrue();
        assertThat(ServerEconomyProviders.isPresent("NotHere")).isFalse();
    }

    @Test
    @DisplayName("what an economy wants where the player goes is what it is given")
    void whatAneconomyWantsIsWhatItIsGiven() {
        Player ada = server.addPlayer("Ada");
        PlayerArguments arguments = PlayerArguments.ofServer();

        assertThat(arguments.of(Argument.PLAYER_ID, ada.getUniqueId())).isEqualTo(ada.getUniqueId());
        assertThat(arguments.of(Argument.PLAYER_NAME, ada.getUniqueId())).isEqualTo("Ada");
        assertThat(arguments.of(Argument.OFFLINE_PLAYER, ada.getUniqueId()))
                .describedAs("a player who is here is handed over as the live one, as before")
                .isSameAs(ada);
    }

    /**
     * The half that did not exist. An economy is asked about an id the server has never had a player for,
     * which is what uxmAuction does when it pays a seller who has not logged in since the listing sold.
     */
    @Test
    @DisplayName("an id with nobody behind it is still answered in every shape")
    void anAbsentIdIsStillAnswered() {
        PlayerArguments arguments = PlayerArguments.ofServer();
        UUID absent = UUID.randomUUID();

        assertThat(arguments.of(Argument.PLAYER_ID, absent)).isEqualTo(absent);
        assertThat(arguments.of(Argument.OFFLINE_PLAYER, absent)).isInstanceOf(OfflinePlayer.class);
        assertThat(arguments.of(Argument.PLAYER_NAME, absent))
                .describedAs("a null name would reach the economy as a missing argument")
                .isNotNull();
    }

    /** A logger that keeps what was said at warning level and above. */
    private static final class Recording implements System.Logger {

        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(
                Level level,
                java.util.@Nullable ResourceBundle bundle,
                @Nullable String msg,
                @Nullable Throwable thrown) {
            if (level.getSeverity() >= Level.WARNING.getSeverity()) {
                warnings.add(String.valueOf(msg));
            }
        }

        @Override
        public void log(
                Level level,
                java.util.@Nullable ResourceBundle bundle,
                @Nullable String format,
                @Nullable Object... params) {
            if (level.getSeverity() >= Level.WARNING.getSeverity()) {
                warnings.add(String.valueOf(format));
            }
        }
    }

    /** One description of the economy this test registered, in the shape the reader reads. */
    private static EconomyBinding binding(Access access, @Nullable String accessor, String className) {
        return new EconomyBinding(
                "Money",
                className,
                access,
                accessor,
                "getBalance",
                "withdrawPlayer",
                Argument.OFFLINE_PLAYER,
                Answer.VAULT_RESPONSE,
                Pools.one(),
                Calls.simple());
    }
}
