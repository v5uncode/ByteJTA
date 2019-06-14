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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bytesoft.transaction.TransactionBeanFactory;
import org.bytesoft.transaction.aware.TransactionBeanFactoryAware;
import org.bytesoft.transaction.remote.RemoteCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

public class TransactionBeanRegistry implements TransactionBeanFactoryAware, ApplicationContextAware, EnvironmentAware {
	static final Logger logger = LoggerFactory.getLogger(TransactionBeanRegistry.class);

	private static final TransactionBeanRegistry instance = new TransactionBeanRegistry();

	private RemoteCoordinator consumeCoordinator;
	private ApplicationContext applicationContext;
	private Environment environment;
	@javax.inject.Inject
	private TransactionBeanFactory beanFactory;

	private Lock lock = new ReentrantLock();
	private Condition condition = this.lock.newCondition();

	private TransactionBeanRegistry() {
		if (instance != null) {
			throw new IllegalStateException();
		}
	}

	public static TransactionBeanRegistry getInstance() {
		return instance;
	}

	public RemoteCoordinator getConsumeCoordinator() {
		if (this.consumeCoordinator != null) {
			return this.consumeCoordinator;
		} else {
			return this.doGetConsumeCoordinator();
		}
	}

	private RemoteCoordinator doGetConsumeCoordinator() {
		try {
			this.lock.lock();
			while (this.consumeCoordinator == null) {
				try {
					this.condition.await(1, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					logger.debug(ex.getMessage());
				}
			}

			// ConsumeCoordinator is injected by the TransactionConfigPostProcessor, which has a slight delay.
			return consumeCoordinator;
		} finally {
			this.lock.unlock();
		}
	}

	public void setConsumeCoordinator(RemoteCoordinator consumeCoordinator) {
		try {
			this.lock.lock();
			if (this.consumeCoordinator == null) {
				this.consumeCoordinator = consumeCoordinator;
				this.condition.signalAll();
			} else {
				throw new IllegalStateException(
						"Field 'consumeCoordinator' has already been set, please check your app whether it imports ByteJTA repeatedly!");
			}
		} finally {
			this.lock.unlock();
		}
	}

	public <T> T getBean(Class<T> requiredType) {
		try {
			return this.applicationContext.getBean(requiredType);
		} catch (NoSuchBeanDefinitionException error) {
			return null; // ignore
		}
	}

	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public void setBeanFactory(TransactionBeanFactory tbf) {
		this.beanFactory = tbf;
	}

	public TransactionBeanFactory getBeanFactory() {
		return beanFactory;
	}

}
