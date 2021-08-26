
/**
 * Copyright 2012 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.virjar.ratel.backup.v2.copy.hystrix;


/**
 * Various states/events that can be captured in the {@link HystrixRollingNumber}.
 * <p>
 * Note that events are defined as different types:
 * <ul>
 * <li>Counter: <code>isCounter() == true</code></li>
 * <li>MaxUpdater: <code>isMaxUpdater() == true</code></li>
 * </ul>
 * <p>
 * The Counter type events can be used with {@link HystrixRollingNumber#increment}, {@link HystrixRollingNumber#add}, {@link HystrixRollingNumber#getRollingSum} and others.
 * <p>
 * The MaxUpdater type events can be used with {@link HystrixRollingNumber#updateRollingMax} and {@link HystrixRollingNumber#getRollingMaxValue}.
 */
public enum HystrixRollingNumberEvent {
    SPEED(1);
    private final int type;

    HystrixRollingNumberEvent(int type) {
        this.type = type;
    }

    public boolean isCounter() {
        return type == 1;
    }

    public boolean isMaxUpdater() {
        return type == 2;
    }

}
