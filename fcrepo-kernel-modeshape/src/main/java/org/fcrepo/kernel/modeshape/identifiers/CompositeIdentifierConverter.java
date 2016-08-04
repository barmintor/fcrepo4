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
package org.fcrepo.kernel.modeshape.identifiers;


/**
 * 
 * @author barmintor
 *
 * @param <A>
 * @param <B>
 * @param <C>
 */
public class CompositeIdentifierConverter<A, B, C> extends IdentifierConverter<A, C> {

    final IdentifierConverter<A,B> first;
    final IdentifierConverter<B,A> firstReverse;
    final IdentifierConverter<B,C> second;
    final IdentifierConverter<C,B> secondReverse;

    CompositeIdentifierConverter(final IdentifierConverter<A,B> first, final IdentifierConverter<B,C> second) {
        this.first = first;
        this.firstReverse = first.inverse();
        this.second = second;
        this.secondReverse = second.inverse();
    }

    @Override
    public boolean inDomain(final A input) {
        return first.inDomain(input);
    }

    @Override
    public A toDomain(final C resource) {
        return first.toDomain(second.toDomain(resource));
    }

    @Override
    public String asString(final A resource) {
        return second.asString(first.apply(resource));
    }

    @Override
    public C apply(final A a) {
        return second.apply(first.apply(a));
    }

}