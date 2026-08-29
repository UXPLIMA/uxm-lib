/**
 * <b>EXPERIMENTAL: packet foundation only.</b> This module is the from-scratch, MIT-clean groundwork for a
 * future fake-entity NPC layer. It deliberately ships <em>only</em> the Netty-pipeline plumbing; there is no
 * NPC, no entity spawning, no skin/tablist code yet. PacketEvents (the obvious off-the-shelf choice) is GPL,
 * so none of it is borrowed; the technique here is modelled on the MIT HamsterAPI pipeline-injection
 * blueprint and re-implemented for Paper 1.21+.
 *
 * <p>What is real and usable today:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.npc.PacketListener}: the seam: {@code onSend}/{@code onReceive} return a
 *       {@link com.uxplima.uxmlib.npc.PacketAction} (pass or cancel). A listener may instead override
 *       {@code onSendVerdict} to return a {@link com.uxplima.uxmlib.npc.PacketVerdict}, which also supports a
 *       {@code rewrite} that forwards a replacement packet downstream on the outbound path. Listeners never
 *       throw across the channel; faults are swallowed and the packet passes (fail-open).
 *   <li>{@link com.uxplima.uxmlib.npc.PacketListenerRegistry}: an ordered, thread-safe registry that
 *       dispatches a packet to every listener and folds their decisions into a single pass/cancel/rewrite
 *       verdict (cancel vetoes; the first rewrite otherwise wins). Pure logic; fully unit-tested.
 *   <li>{@link com.uxplima.uxmlib.npc.PipelineWatchdog}: the self-healing reorder decision: given the live
 *       handler names, our handler name and its anchor, it decides whether our handler still sits directly
 *       after the anchor and, if not, what move restores it. Pure logic; fully unit-tested.
 *   <li>{@link com.uxplima.uxmlib.npc.ChannelResolver}: the single class that holds the unavoidable NMS
 *       reflection: {@code CraftPlayer.getHandle() -> connection -> channel}, reached by field <em>type</em>
 *       not obfuscated name, every step guarded. Returns an {@link java.util.Optional}; it never throws.
 *   <li>{@link com.uxplima.uxmlib.npc.PacketPipeline}: the injector: inject/eject a named
 *       {@link io.netty.channel.ChannelDuplexHandler} into a player's connection channel, idempotently, with
 *       a {@link com.uxplima.uxmlib.npc.PipelineWatchdog}-driven reorder pass.
 * </ul>
 *
 * <p>What is <em>not</em> here (and why a server cannot yet show an NPC with this module alone): packet
 * <em>encoding</em> (we forward raw netty messages but do not construct spawn/metadata/equipment packets),
 * any entity-id allocator, per-viewer interest tracking, or skin resolution. Those are the next milestone and
 * will reuse the holograms' two-set per-viewer lifecycle. Treat every type here as unstable API.
 *
 * <p>MockBukkit cannot provide a real Netty channel, so {@link com.uxplima.uxmlib.npc.ChannelResolver} and
 * {@link com.uxplima.uxmlib.npc.PacketPipeline} are smoke-tested only: the contract under test is that they
 * fail gracefully (return empty / report not-injected) against a mock player rather than throwing.
 */
@NullMarked
package com.uxplima.uxmlib.npc;

import org.jspecify.annotations.NullMarked;
