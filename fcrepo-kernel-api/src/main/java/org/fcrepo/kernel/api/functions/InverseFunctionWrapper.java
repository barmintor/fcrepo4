/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.api.functions;


/**
 * Simple inverse function that wraps an InvertibleFunction implementation
 * Assumes a bijective function.
 * @author barmintor
 *
 */
public class InverseFunctionWrapper<A, B> implements InjectiveFunction<A, B> {

    private final InjectiveFunction<B, A> original;
    /**
     * 
     * @param original
     */
    public InverseFunctionWrapper(final InjectiveFunction<B, A> original) {
        this.original = original;
    }

    @Override
    public B apply(final A t) {
        return original.toDomain(t);
    }

    @Override
    public InvertibleFunction<B, A> inverse() {
        return original;
    }

    @Override
    public A toDomain(final B rangeValue) {
        return original.apply(rangeValue);
    }
}
