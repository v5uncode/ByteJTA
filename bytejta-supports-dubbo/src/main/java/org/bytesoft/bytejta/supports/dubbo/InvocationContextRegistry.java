/**
 * Copyright 2014-2016 yangming.liu<bytefox@126.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.bytejta.supports.dubbo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bytesoft.transaction.remote.RemoteNode;

public final class InvocationContextRegistry {
	private static final InvocationContextRegistry instance = new InvocationContextRegistry();

	private final Map<Thread, RemoteNode> contexts = new ConcurrentHashMap<Thread, RemoteNode>();

	private InvocationContextRegistry() {
		if (instance != null) {
			throw new IllegalStateException();
		}
	}

	public static InvocationContextRegistry getInstance() {
		return instance;
	}

	public void associateInvocationContext(RemoteNode context) {
		this.contexts.put(Thread.currentThread(), context);
	}

	public RemoteNode desociateInvocationContext() {
		return this.contexts.remove(Thread.currentThread());
	}

	public RemoteNode getInvocationContext() {
		return this.contexts.get(Thread.currentThread());
	}

}
