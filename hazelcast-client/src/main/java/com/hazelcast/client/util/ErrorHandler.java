/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.util;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.util.ExceptionUtil;

import java.io.IOException;

import static com.hazelcast.util.ExceptionUtil.fixRemoteStackTrace;

/**
 * @ali 7/2/13
 */
public class ErrorHandler {

    public static <T> T returnResultOrThrowException(Object result) {
        if (result instanceof Throwable) {
            fixRemoteStackTrace((Throwable) result, Thread.currentThread().getStackTrace());
            ExceptionUtil.sneakyThrow((Throwable) result);
            return null; // not accessible!
        } else {
            return (T) result;
        }
    }

    public static boolean isRetryable(Exception e) {
        return e instanceof IOException || e instanceof HazelcastInstanceNotActiveException;
    }

    private ErrorHandler(){}
}
