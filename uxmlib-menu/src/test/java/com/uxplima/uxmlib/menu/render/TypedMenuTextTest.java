package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.MenuBasics;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.ActionArguments;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.MenuControl;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.ClickSpec;
import com.uxplima.uxmlib.menu.spec.ItemDecor;
import com.uxplima.uxmlib.menu.spec.ItemType;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.SlotSet;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * What a player types into a menu argument, a prompt or a form reads back as the words they typed wherever a menu line
 * shows it, and reaches the menu's data and actions as they typed it. It never becomes a tag, never ends a quoted
 * argument or a catalog argument early, and is never read again for a placeholder it spells.
 */
class TypedMenuTextTest {

    /** A click tag that would run a command for whoever clicks the line, if it were read as markup. */
    private static final String CLICK = "<click:run_command:'/op Alex'>here";

    private final List<Component> told = new ArrayList<>();

    private final Player viewer = (Player) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "isOnline" -> true;
                case "sendMessage" -> args.length == 1 && args[0] instanceof Component line && told.add(line);
                default -> null;
            });

    private final Catalog catalog = new Catalog();

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private final ItemRenderer renderer = new ItemRenderer(catalog, Theme::defaults, placeholders);

    TypedMenuTextTest() {
        placeholders.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "secret-" + id);
        placeholders.register("balance", ctx -> "<gold>100");
        catalog.entries.put("menu.warp", "Warp to {name} {extra}");
    }

    @Test
    void aTypedClickTagInALineReadsAsTheWordsTyped() {
        Component title = renderer.title("Warp to %argument_name%", opened(Map.of("name", CLICK)));

        assertThat(plain(title)).isEqualTo("Warp to " + CLICK);
        assertThat(clicks(title)).isEmpty();
    }

    @Test
    void aTypedQuoteCannotLeaveTheClickArgumentItSitsIn() {
        String typed = "spawn'><click:run_command:'/op Alex'>x \\";

        Component title =
                renderer.title("<click:run_command:'/warp %argument_name%'>Go", opened(Map.of("name", typed)));

        assertThat(plain(title)).isEqualTo("Go");
        assertThat(clicks(title)).containsExactly(ClickEvent.runCommand("/warp " + typed));
    }

    @Test
    void aTypedValueInAHoverReadsAsTheWordsTyped() {
        String typed = "<red>'boom\" \\";

        Component title =
                renderer.title("<hover:show_text:'Hello %argument_name%'>Hover", opened(Map.of("name", typed)));

        assertThat(plain(title)).isEqualTo("Hover");
        assertThat(hoverTexts(title)).containsExactly("Hello " + typed);
    }

    @Test
    void aTypedClickTagInAKeyArgumentReadsAsTheWordsTyped() {
        Component unquoted = renderer.title("@menu.warp name=%argument_name%", opened(Map.of("name", CLICK)));
        Component quoted = renderer.title("@menu.warp name=\"%argument_name%\"", opened(Map.of("name", CLICK)));

        assertThat(plain(unquoted)).isEqualTo("Warp to " + CLICK + " {extra}");
        assertThat(plain(quoted)).isEqualTo("Warp to " + CLICK + " {extra}");
        assertThat(clicks(unquoted)).isEmpty();
        assertThat(clicks(quoted)).isEmpty();
    }

    @Test
    void aTypedValueCannotEndAKeyArgumentOrAddOne() {
        String spaced = "Alex extra=<red>injected";
        String quoting = "Alex\" extra=\"<red>injected";

        Component unquoted = renderer.title("@menu.warp name=%argument_name%", opened(Map.of("name", spaced)));
        Component quoted = renderer.title("@menu.warp name=\"%argument_name%\"", opened(Map.of("name", quoting)));

        assertThat(plain(unquoted)).isEqualTo("Warp to " + spaced + " {extra}");
        assertThat(plain(quoted)).isEqualTo("Warp to " + quoting + " {extra}");
    }

    @Test
    void aTypedValueInAKeyKeepsOnlyKeyCharacters() {
        catalog.entries.put("shop.weapons.title", "Weapons");

        Component picked = renderer.title("@shop.%argument_category%.title", opened(Map.of("category", "weapons")));
        Component forged =
                renderer.title("@shop.%argument_category%.title", opened(Map.of("category", "x name=<red>y")));

        assertThat(plain(picked)).isEqualTo("Weapons");
        assertThat(plain(forged)).isEqualTo("shop.xnameredy.title");
        assertThat(catalog.asked).containsExactly("shop.weapons.title", "shop.xnameredy.title");
    }

    @Test
    void aTypedValueIsNeverReadAgainForAPlaceholder() {
        String typed = "%argument_secret% %papi_player_ip% %balance%";

        Component title =
                renderer.title("%argument_name% / %papi_player_ip%", opened(Map.of("name", typed, "secret", "hidden")));

        assertThat(plain(title)).isEqualTo(typed + " / secret-papi_player_ip");
    }

    @Test
    void aPromptAnswerReadsAsTheWordsTypedAndIsNotATemplate() {
        String typed = "<red>%papi_player_ip% %argument_name%";
        MenuContext answered = opened(Map.of("name", "Alex")).withTypedLocals(Map.of("input", typed));

        Component title = renderer.title("Rename to %input%?", answered);

        assertThat(plain(title)).isEqualTo("Rename to " + typed + "?");
    }

    @Test
    void anOperatorTemplateStillExpandsAndEscapesWhatWasTypedInIt() {
        MenuContext ctx = opened(Map.of("name", CLICK))
                .withLocalPlaceholders(Map.of("greet", "<click:suggest_command:'/msg %argument_name% '>%balance%"));

        Component title = renderer.title("%greet%", ctx);

        assertThat(plain(title)).isEqualTo("100");
        assertThat(clicks(title)).containsExactly(ClickEvent.suggestCommand("/msg " + CLICK + " "));
    }

    @Test
    void anArgumentTheOpenerVouchesForIsDrawnAsMarkup() {
        MenuContext ctx =
                opened(Map.of("rank", "<red>Admin", "name", "<red>Alex")).withMarkupArguments(Set.of("rank"));

        Component title = renderer.title("%argument_rank% %argument_name%", ctx);

        assertThat(plain(title)).isEqualTo("Admin <red>Alex");
        assertThat(inRed(title, null)).isEqualTo("Admin <red>Alex");
    }

    @Test
    void aTypedValueReachesTheMaterialAsTyped() {
        MenuItemSpec item = new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "skull:%argument_player%",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);

        assertThat(renderer.materialSpec(item, opened(Map.of("player", "Al<ex\\"))))
                .isEqualTo("skull:Al<ex\\");
    }

    @Test
    void anActionReadsEachValueAsTypedAndOnce() {
        MenuContext ctx = opened(Map.of("name", "%input% <red>%greet%"))
                .withLocalPlaceholders(Map.of("greet", "hello"))
                .withTypedLocals(Map.of("input", "%argument_name%"));

        Map<String, String> args = ActionArguments.resolve(
                Map.of("to", "%argument_name%", "said", "%input%", "plain", "%greet% %papi_x%"), ctx);

        assertThat(args)
                .containsEntry("to", "%input% <red>%greet%")
                .containsEntry("said", "%argument_name%")
                .containsEntry("plain", "hello %papi_x%");
    }

    @Test
    void aMessageActionSendsWhatWasTypedAsWords() {
        MenuBindings bindings = new MenuBindings();
        MenuBasics.register(bindings);
        MenuContext ctx = opened(Map.of("name", CLICK)).withTypedLocals(Map.of("input", "<b>%argument_name%"));

        bindings.actions()
                .get("message")
                .orElseThrow()
                .accept(MenuActionContext.written(
                        ctx,
                        viewer,
                        ClickKind.LEFT,
                        Map.of("value", "<green>%argument_name% said %input%"),
                        MenuControl.NOOP));

        assertThat(told).hasSize(1);
        assertThat(plain(told.getFirst())).isEqualTo(CLICK + " said <b>%argument_name%");
        assertThat(clicks(told.getFirst())).isEmpty();
        assertThat(inGreen(told.getFirst(), null)).isEqualTo(CLICK + " said <b>%argument_name%");
    }

    @Test
    void aCommandActionRunsWhatWasTypedAsTyped() {
        MenuActionContext click = MenuActionContext.written(
                opened(Map.of("name", "Al<ex")),
                viewer,
                ClickKind.LEFT,
                Map.of("value", "msg %argument_name% hi"),
                MenuControl.NOOP);

        assertThat(click.arg()).isEqualTo("msg Al<ex hi");
        assertThat(click.textArg()).isEqualTo("msg Al\\<ex hi");
    }

    private MenuContext opened(Map<String, String> arguments) {
        return MenuContext.of(viewer, null, 0, arguments);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<ClickEvent<?>> clicks(Component component) {
        List<ClickEvent<?>> found = new ArrayList<>();
        walk(component, part -> {
            if (part.clickEvent() != null) {
                found.add(part.clickEvent());
            }
        });
        return found;
    }

    private static List<String> hoverTexts(Component component) {
        List<String> found = new ArrayList<>();
        walk(component, part -> {
            HoverEvent<?> hover = part.hoverEvent();
            if (hover != null && hover.value() instanceof Component text) {
                found.add(plain(text));
            }
        });
        return found;
    }

    /** The words of {@code component} drawn in green, reading a colour a part leaves unset from the part around it. */
    private static String inGreen(Component component, @Nullable TextColor around) {
        TextColor colour = component.color() != null ? component.color() : around;
        StringBuilder green = new StringBuilder();
        if (component instanceof TextComponent text && NamedTextColor.GREEN.equals(colour)) {
            green.append(text.content());
        }
        component.children().forEach(child -> green.append(inGreen(child, colour)));
        return green.toString();
    }

    /** The words of {@code component} drawn in red, reading a colour a part leaves unset from the part around it. */
    private static String inRed(Component component, @Nullable TextColor around) {
        TextColor colour = component.color() != null ? component.color() : around;
        StringBuilder red = new StringBuilder();
        if (component instanceof TextComponent text && NamedTextColor.RED.equals(colour)) {
            red.append(text.content());
        }
        component.children().forEach(child -> red.append(inRed(child, colour)));
        return red.toString();
    }

    private static void walk(Component component, java.util.function.Consumer<Component> visit) {
        visit.accept(component);
        component.children().forEach(child -> walk(child, visit));
    }

    /**
     * A consumer's catalogue: every line is MiniMessage, and a {@code {name}} in an entry is filled with the value the
     * line or the menu gives for it, before the entry is read, the way a consumer that trusts the engine's values does.
     */
    private static final class Catalog implements GuiText {

        private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z0-9_.-]+)\\}");

        private final Map<String, String> entries = new java.util.HashMap<>();
        private final List<String> asked = new ArrayList<>();

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            asked.add(key);
            String template = entries.getOrDefault(key, key);
            Matcher matcher = TOKEN.matcher(template);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String value = placeholders.get(matcher.group(1));
                matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
            }
            matcher.appendTail(out);
            return MiniMessage.miniMessage().deserialize(out.toString());
        }

        @Override
        public Component render(String raw) {
            return MiniMessage.miniMessage().deserialize(raw);
        }
    }
}
