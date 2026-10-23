/**
 * Storage tests on real servers. {@link com.uxplima.uxmlib.storage.testing.EveryBackend} runs one test once for
 * each {@link com.uxplima.uxmlib.storage.sql.Backend}, each time on an empty database of its own, and
 * {@link com.uxplima.uxmlib.storage.testing.RealDatabases} starts the servers it needs in containers.
 */
@NullMarked
package com.uxplima.uxmlib.storage.testing;

import org.jspecify.annotations.NullMarked;
