/**
 * Schematics in a running world: {@link com.uxplima.uxmlib.schematic.paper.SchematicPaster} puts one down and
 * {@link com.uxplima.uxmlib.schematic.paper.SchematicCapture} takes one up, a chunk at a time on the thread
 * that owns it, so both are safe on Folia and neither stalls a tick.
 *
 * <p>A block's state is resolved by name, and a name a newer game changed is carried to its current one.
 * Items and entities go through Paper's own serialisation, which upgrades a file from an older game the way
 * the server upgrades its own worlds. A block entity, a chest's items or a sign's text, is carried by kind
 * through the API, since Paper offers no serialisation for them.
 */
@NullMarked
package com.uxplima.uxmlib.schematic.paper;

import org.jspecify.annotations.NullMarked;
