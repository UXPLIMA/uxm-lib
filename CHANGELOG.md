# Changelog

## 0.0.1 (2026-10-27)


### ⚠ BREAKING CHANGES

* **build:** versions restart at 0.0.1, below the old 0.x line.
* gui.config leaves, and the two classes worth keeping move
* **gui:** the public type com.uxplima.uxmlib.gui.ConfirmMenu is removed. It shipped in every release from 0.10.0 to 0.42.0, so a consumer upgrading from any of them sees a compile error, and this line is the only notice they get.
* **menu:** MenuSounds gains a fourth component. Any caller using its canonical constructor rather than from() must pass a page sound.
* **menu:** the editor click context stops sharing a name with the gui one
* com.uxplima.uxmlib.config.MapMerge is package private. Nothing in this library called it and the README never named it, so no documented use of it exists, but a source break is a source break and it is said out loud here.
* com.uxplima.uxmlib.item.Lore is now LoreWrap, and com.uxplima.uxmlib.hook.Placeholders is now PlaceholderApi. Methods, signatures and behaviour are unchanged.
* the coordinate com.uxplima.uxmlib:uxmlib-npc is now com.uxplima.uxmlib:uxmlib-pipeline, and the package com.uxplima.uxmlib.npc is now com.uxplima.uxmlib.pipeline. Type names are unchanged.
* uxmLib no longer loads on a Paper 1.21 server. A consumer on that line stays on the previous release.
* com.uxplima.uxmlib.config.MapMerge is package private. Nothing in this library called it and the README never named it, so no documented use of it exists, but a source break is a source break and it is said out loud here.
* com.uxplima.uxmlib.item.Lore is now LoreWrap, and com.uxplima.uxmlib.hook.Placeholders is now PlaceholderApi. Methods, signatures and behaviour are unchanged.
* the coordinate com.uxplima.uxmlib:uxmlib-npc is now com.uxplima.uxmlib:uxmlib-pipeline, and the package com.uxplima.uxmlib.npc is now com.uxplima.uxmlib.pipeline. Type names are unchanged.
* uxmLib no longer loads on a Paper 1.21 server. A consumer on that line stays on the previous release.

### Features

* **backup:** a seam for a plugin that must save before its files are copied ([7c95987](https://github.com/UXPLIMA/uxm-lib/commit/7c959878d3b3e39c72e2e58674124d3bbec025cb))
* **command:** read a plain subcommand switch ([0c2ba7d](https://github.com/UXPLIMA/uxm-lib/commit/0c2ba7df57f37f7badae5d337c7981e3161dede7))
* **condition:** a wallet can pay out as well as take ([fe7b5fc](https://github.com/UXPLIMA/uxm-lib/commit/fe7b5fc099902fd7efa6815e90a8ca45da13a373))
* **condition:** add money and item conditions with take-money and take-item ([74c155e](https://github.com/UXPLIMA/uxm-lib/commit/74c155e1007b0b9417e42b26e748171d3e39911c))
* **condition:** count the player's own experience as a wallet ([580d7e4](https://github.com/UXPLIMA/uxm-lib/commit/580d7e413c1b0de4d8382544526f6ecdc4a0dde1))
* **condition:** read a balance from a placeholder and take it with a command ([b1a9118](https://github.com/UXPLIMA/uxm-lib/commit/b1a91185bf5ff3906cdc0b728bd82ac887f59532))
* **condition:** read a balance from an economy described, not compiled against ([7c416e1](https://github.com/UXPLIMA/uxm-lib/commit/7c416e1b281b55cbd18db67a90422f1fb416f92e))
* **condition:** read a balance from Treasury ([0b1fd0a](https://github.com/UXPLIMA/uxm-lib/commit/0b1fd0a1f872031db0596f9b3624087e2ea8550b))
* **gui:** a menu's page turn is its own sound, and a misspelt one still plays ([0f217ec](https://github.com/UXPLIMA/uxm-lib/commit/0f217ec6ee7936d8d866c132d93bcded12051b91))
* **gui:** a tile may draw a fact as a state ([ceed649](https://github.com/UXPLIMA/uxm-lib/commit/ceed6490f2b78acca3809aa58d746a74852bd5e1))
* **gui:** GuiText answers the question an inventory title asks ([8d72b67](https://github.com/UXPLIMA/uxm-lib/commit/8d72b67c8fe5348cb865a7df31cdb794caab1cdb))
* **gui:** PlayerInput can leave the cancel word to its caller ([82c9229](https://github.com/UXPLIMA/uxm-lib/commit/82c922946b21dca3a6588a95f71cdf067b0a2359))
* **gui:** read a menu described by slots, priorities and a computed list ([bb84652](https://github.com/UXPLIMA/uxm-lib/commit/bb846526d944f412d4a5cb7e9ea0d3d1bd39fdf0))
* **gui:** remove ConfirmMenu, whose window was an opinion and not a mechanism ([1542180](https://github.com/UXPLIMA/uxm-lib/commit/1542180b9771c4593386c4b426eedfb8919fa616))
* **gui:** the seam a menu gets its words through ([9749583](https://github.com/UXPLIMA/uxm-lib/commit/9749583c07cdd2b11dfa65aa75d430ac498fe511))
* **gui:** the text input seam joins the mechanism it was already built on ([4cb3340](https://github.com/UXPLIMA/uxm-lib/commit/4cb33409b8ab53d2179d222a646399c7713e112e))
* **gui:** Tiles titles a lore that is a list of lines ([e90ff97](https://github.com/UXPLIMA/uxm-lib/commit/e90ff97409b52da04f857c67dfea4833fe2a5282))
* **gui:** wrap a description that is too long for a tooltip ([5eb6400](https://github.com/UXPLIMA/uxm-lib/commit/5eb6400e1c10a2366dffdbe619ff7ee878ad5607))
* **hook:** answer a flat placeholder namespace with one provider ([85a5cea](https://github.com/UXPLIMA/uxm-lib/commit/85a5ceaf90f13dc8a156a7a9d8a93ddc6b69ce8b))
* **hud:** one nametag registry for one server, across jars ([22480a6](https://github.com/UXPLIMA/uxm-lib/commit/22480a68dd16b875a616321c09b79484a1b31975))
* **integration:** one claim seam for the whole estate ([55a7208](https://github.com/UXPLIMA/uxm-lib/commit/55a72087e164e9410301ac459dae41ca3ba653f5))
* **item:** a whole item written down as one line of text ([3df848f](https://github.com/UXPLIMA/uxm-lib/commit/3df848fbc0e2f0d7d903ef6454d74209e9736114))
* **item:** ItemConfig reads the other half of an item ([94668cb](https://github.com/UXPLIMA/uxm-lib/commit/94668cb94fcf9fbca5d2b6d9df3cb06da9e617f7))
* **menu:** a list entry may end its page ([03814e9](https://github.com/UXPLIMA/uxm-lib/commit/03814e9532d2b924b2c72acdd5428d7ef44e484d))
* **menu:** let a tile line name its own closing sentence ([62173dd](https://github.com/UXPLIMA/uxm-lib/commit/62173dda91f6c1d6a640d821f7e461e98467d0c1))
* **menu:** put PlaceholderRegistry.resolveOrReport back ([98f604c](https://github.com/UXPLIMA/uxm-lib/commit/98f604c86adfa9f6c9b0c08fa9fe19a9165d93ac))
* **menu:** the evaluator, the second leaf of the engine ([eefd9b4](https://github.com/UXPLIMA/uxm-lib/commit/eefd9b47e01344cd36aa2c4241190bfd86db1e5b))
* **menu:** the five verbs a menu file writes in every plugin ([c45f87b](https://github.com/UXPLIMA/uxm-lib/commit/c45f87bed6f68e21fce62cd9d21d339f688e72f3))
* **menu:** the menu engine, off its old plugin and onto this library ([f7d3d55](https://github.com/UXPLIMA/uxm-lib/commit/f7d3d551d9320dd95f95d8dc4a508536a391d30f))
* **menu:** the module, and the spec grammar it reads ([132f60d](https://github.com/UXPLIMA/uxm-lib/commit/132f60d850474f6ed287b24491e6a1d1389676d7))
* **menu:** the page tokens the library documented but never shipped ([5b626fb](https://github.com/UXPLIMA/uxm-lib/commit/5b626fb05e785843f91e484488e8003bce0c6e33))
* **menu:** write a menu spec back to its file ([1c0a197](https://github.com/UXPLIMA/uxm-lib/commit/1c0a1978059bf0a65749eb72f75804374bdd130c))
* **packet:** a hologram that is sent and never spawned ([5295f01](https://github.com/UXPLIMA/uxm-lib/commit/5295f019c85b5b7f41cff088b466f4cdfc8ebdb2))
* **packet:** rewrite the item a client is shown, never the item the server holds ([bdd256a](https://github.com/UXPLIMA/uxm-lib/commit/bdd256ade59f3e0ece55bfad8a3f0601c506f2b8))
* **redis:** add real RedisStreamBus and LettuceRedisStreamBus with XADD, XREADGROUP, XACK, XAUTOCLAIM ([5d34a0b](https://github.com/UXPLIMA/uxm-lib/commit/5d34a0b073e0be1ab933606efe8cbd27fe655ec3))
* **style:** a header token may name a wheel position ([be4241e](https://github.com/UXPLIMA/uxm-lib/commit/be4241e58ed0d743a86d86b117d7d09749246370))
* **style:** a name may be painted without being a heading ([2c97cb0](https://github.com/UXPLIMA/uxm-lib/commit/2c97cb0e4f90e16e89d4d435299bf2858c3ad01c))
* **style:** a theme is a palette, open roles and a wheel ([95ce7b5](https://github.com/UXPLIMA/uxm-lib/commit/95ce7b52e0744b844daea2d0125166f2c3d21a27))
* **style:** a title takes a tone of its own ([cef707b](https://github.com/UXPLIMA/uxm-lib/commit/cef707bb30fd6fd1896bcbfd1679583708275f84))


### Bug Fixes

* **command:** register the flags node as a Paper custom argument type ([07d16ad](https://github.com/UXPLIMA/uxm-lib/commit/07d16ad4eb146941284b4fcebb939228ccf979c1))
* **config:** a read of a HOCON value no longer writes one ([62938ea](https://github.com/UXPLIMA/uxm-lib/commit/62938eab710a50168144d857b4b78b6ba2169556))
* **gui:** a label that flattens to nothing says so ([a732d92](https://github.com/UXPLIMA/uxm-lib/commit/a732d927a3b80520fc7a2019263b7649f78618b9))
* **gui:** a label that loses its words says its key instead of nothing ([576db03](https://github.com/UXPLIMA/uxm-lib/commit/576db033a86ccc28e1a576725c3e510507d5aaa7))
* **gui:** a sound name in the case the server prints it is the same sound ([ea41bdf](https://github.com/UXPLIMA/uxm-lib/commit/ea41bdf0f35a8865e4662b28f99d10f7495be199))
* **gui:** give the renamed dialog button keys a way back ([c79b975](https://github.com/UXPLIMA/uxm-lib/commit/c79b9756072b970a734b1bc8ded459d6bdf7c809))
* **gui:** the constant sound spelling is refused, not silently turned into nothing ([cdd2888](https://github.com/UXPLIMA/uxm-lib/commit/cdd2888189953740e10b96c6f693fa5ef6aa72fb))
* **gui:** the constant sound spelling keeps a home the delete did not take ([9ca635c](https://github.com/UXPLIMA/uxm-lib/commit/9ca635cdd331bf638bad16150ce8aa680e5a8fe5))
* **gui:** the dialog prompt's two buttons take their words from the caller ([f3b04a5](https://github.com/UXPLIMA/uxm-lib/commit/f3b04a545bff1ac8895ae007c31eb9e86b3914d7))
* **gui:** the rule a titled tile is decided by is asked, not restated ([db96591](https://github.com/UXPLIMA/uxm-lib/commit/db965915dfe944c222522ea4d76507ef62af5ede))
* **menu:** a content region that is handed more stacks than it has slots says so ([8215cce](https://github.com/UXPLIMA/uxm-lib/commit/8215cceabed3b3424aa4b98e6043597336ca13e1))
* **menu:** a control cannot sit on a page button, and a broken handler is said once ([1386af1](https://github.com/UXPLIMA/uxm-lib/commit/1386af1b49b096b2a116dcc174f6b9d2deba863d))
* **menu:** a flag name is read like every other name in an item block ([3b38999](https://github.com/UXPLIMA/uxm-lib/commit/3b389992e2c3400efb84727d9ffd8e4d75b4cbca))
* **menu:** a layout too small for what it must hold now says so ([c996a83](https://github.com/UXPLIMA/uxm-lib/commit/c996a83cbf22d5d92e44af231efe9e4a08c95fb4))
* **menu:** a list reads the view blocks its file writes ([2db45e2](https://github.com/UXPLIMA/uxm-lib/commit/2db45e2faf334e7bd9ee0ca40b723c684087f65f))
* **menu:** a material spec keeps the words around its token ([5b0e7e9](https://github.com/UXPLIMA/uxm-lib/commit/5b0e7e9c20ee700db0fc237b6c662838baf6d7bb))
* **menu:** a math block missing an operand shows a gap whichever operator it used ([178e0c3](https://github.com/UXPLIMA/uxm-lib/commit/178e0c3549ff58349e58f3c93aca6c18493c631d))
* **menu:** a menu whose lists share a cell is refused rather than drawn at random ([1ad15ba](https://github.com/UXPLIMA/uxm-lib/commit/1ad15ba33a24dfd3aed451ece1b1fc6c9e041bbf))
* **menu:** a menu's first click is not measured against a clock reading it never took ([0d6cdd6](https://github.com/UXPLIMA/uxm-lib/commit/0d6cdd6ca20f7e15338d9d9cc712faed5c2f26e9))
* **menu:** a number literal too large for a double is refused where it is read ([b542cd3](https://github.com/UXPLIMA/uxm-lib/commit/b542cd38bfa8a03021419b73938ecc783a279a17))
* **menu:** a picker slot the file cannot spell is told apart from one it never wrote ([d29c162](https://github.com/UXPLIMA/uxm-lib/commit/d29c1628903ba53aa3b8938a428beb50c1d6a59d))
* **menu:** a picker slot the window cannot address is said out loud, not swallowed ([2d02677](https://github.com/UXPLIMA/uxm-lib/commit/2d02677986ad58e9513f1389d94ebe9b41876835))
* **menu:** a shift-click refused on one content slot skips that slot, not the region ([6cb57df](https://github.com/UXPLIMA/uxm-lib/commit/6cb57dfc3fd94ce2b5e7d291028ddf8da59eb656))
* **menu:** a sign is not a hex digit, so a typed dash is not a colour ([216e4ba](https://github.com/UXPLIMA/uxm-lib/commit/216e4ba57ea64e6a3c990f564bdb3aa92ed0efa5))
* **menu:** an action that refuses stops the ones after it ([2c14c78](https://github.com/UXPLIMA/uxm-lib/commit/2c14c78653116c89fc11b6b3bb9ae9e39f18f45f))
* **menu:** draw a tile line as a tile on every path ([83fdea9](https://github.com/UXPLIMA/uxm-lib/commit/83fdea92f0e21a9d3a6559ba810576335518fce1))
* **menu:** drop a math block with a missing operand in lore ([ec24637](https://github.com/UXPLIMA/uxm-lib/commit/ec24637be0524e403f60d3905e654b414d8df613))
* **menu:** four javadocs stop claiming an exclusivity that was never true ([c53b200](https://github.com/UXPLIMA/uxm-lib/commit/c53b2005221a81cac44c54044cf3cc200564889a))
* **menu:** read a hyphenated enum token, and name one we cannot ([743bf37](https://github.com/UXPLIMA/uxm-lib/commit/743bf376576e8433fa8b7b8bf84bef8a2262e23a))
* **menu:** resolve only the placeholders a line names ([af87acd](https://github.com/UXPLIMA/uxm-lib/commit/af87acdda2ce11305249b411aef6b261d8a10920))
* **menu:** stop the [@key](https://github.com/key) path resolving every placeholder too ([5c5f567](https://github.com/UXPLIMA/uxm-lib/commit/5c5f567a6b73a5f62113a2c0c70d4c617eb22f74))
* **menu:** the arrows drove whichever list the item map happened to hand over first ([a84a4cc](https://github.com/UXPLIMA/uxm-lib/commit/a84a4ccdaee44a974abf0fe89ea808713fcf51e7))
* **menu:** the disable sweep could throw on the first odd view and stop ([b7e9125](https://github.com/UXPLIMA/uxm-lib/commit/b7e91250b80c376653e81bb8f7d5384ad99777c2))
* **menu:** the editor's picker offers every list source a spec may name ([d8c379a](https://github.com/UXPLIMA/uxm-lib/commit/d8c379af4be58d51bbc457ad421dbd5a3db36243))
* **menu:** the engine's tones come from the file, and it forgets one host's words ([65c9960](https://github.com/UXPLIMA/uxm-lib/commit/65c996015af1957e11f7a21ee1e4a19dbcec8aef))
* **menu:** the last resort window checked its row count on the path nobody takes ([f33b07d](https://github.com/UXPLIMA/uxm-lib/commit/f33b07ddaf8728be3e54ad34d79e2c49ebd9822a))
* **menu:** the light level a special icon carries is pinned, and its javadoc corrected ([3231f2a](https://github.com/UXPLIMA/uxm-lib/commit/3231f2a495a15b8f8e21949e795571df16760e1a))
* **menu:** the page indicator could count a different list than the arrows turned ([be09270](https://github.com/UXPLIMA/uxm-lib/commit/be09270fa8af2e7c980a4732f70000704e3f3a04))
* **menu:** two parser messages that told an operator nothing or told it wrong ([1bd332c](https://github.com/UXPLIMA/uxm-lib/commit/1bd332c37b69c776bc5e101a466e6518bcb31cf0))
* **menu:** two refusals for a control column, not one ([0b117c9](https://github.com/UXPLIMA/uxm-lib/commit/0b117c9ae06b4eef95bae9a5a5908e2a057deefc))
* **scheduler:** settle teardown work when the plugin is disabled ([bc906f6](https://github.com/UXPLIMA/uxm-lib/commit/bc906f630a4d5e4dd61720aadb7a086bd27af0a9))
* **style:** stir the hash that picks a tone ([5117a54](https://github.com/UXPLIMA/uxm-lib/commit/5117a54a161974a2f2689973ef91da90237e02e9))
* **text:** a value beats a colour role of the same name ([b43c5a8](https://github.com/UXPLIMA/uxm-lib/commit/b43c5a89e41480b5dac3c5c516c3631f4f0cbd5f))


### Code Refactoring

* **build:** move to uxm-gradle and the repository standard ([#38](https://github.com/UXPLIMA/uxm-lib/issues/38)) ([9d2a471](https://github.com/UXPLIMA/uxm-lib/commit/9d2a4710344e5bac03109c130fdc2c4c2040b6f5))
* gui.config leaves, and the two classes worth keeping move ([4c0fd6e](https://github.com/UXPLIMA/uxm-lib/commit/4c0fd6e3dfef1860290d6cdbeef4f3acefb18db9))
* MapMerge leaves the published API ([5edca40](https://github.com/UXPLIMA/uxm-lib/commit/5edca405d7584ae92d13b1a3b644a4bad1ebf1ac))
* MapMerge leaves the published API ([c613054](https://github.com/UXPLIMA/uxm-lib/commit/c6130542fe43e6a9aa4233c3fe33b8f5c0cbad65))
* **menu:** the editor click context stops sharing a name with the gui one ([3f95c93](https://github.com/UXPLIMA/uxm-lib/commit/3f95c93d345e92578a684141f119c161c5c43513))
* two public names that a caller cannot import together ([fccf85a](https://github.com/UXPLIMA/uxm-lib/commit/fccf85a1b6ea634d36b1040a940dfd80eb1d8d04))
* two public names that a caller cannot import together ([d2bcdb5](https://github.com/UXPLIMA/uxm-lib/commit/d2bcdb5930cb027231858dc0316f6dbdf025854e))
* uxmlib-npc becomes uxmlib-pipeline, and keeps its boundary ([f9e7632](https://github.com/UXPLIMA/uxm-lib/commit/f9e763219b8c2454477fd62693c789a40368a8e6))
* uxmlib-npc becomes uxmlib-pipeline, and keeps its boundary ([7aaec70](https://github.com/UXPLIMA/uxm-lib/commit/7aaec70ed03802450f024fbeca89781ae1232d67))


### Build System

* drop support for everything below Paper 26.2 ([7cc3820](https://github.com/UXPLIMA/uxm-lib/commit/7cc382094f235715e2d0c1c313028fb9a8f55698))
* drop support for everything below Paper 26.2 ([0204a68](https://github.com/UXPLIMA/uxm-lib/commit/0204a68b02a10fdbf23b04823077bcf2b8abdb72))
