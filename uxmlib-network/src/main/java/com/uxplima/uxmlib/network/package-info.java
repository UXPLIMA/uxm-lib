/**
 * Messages between the servers of a network, with no server type in sight.
 *
 * <p>A {@link com.uxplima.uxmlib.network.Bus} carries {@link com.uxplima.uxmlib.network.Frame}s by topic.
 * Two transports implement it: {@link com.uxplima.uxmlib.network.DirectBus}, a connection to a
 * {@link com.uxplima.uxmlib.network.DirectBroker} that a proxy plugin runs, which works with no player
 * online and needs no other service; and {@link com.uxplima.uxmlib.network.RedisNetworkBus} over Redis
 * pub/sub for networks that already run Redis.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.network;
