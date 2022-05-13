

/******************************************************************************
 * Author: Ofek Tal                                                           *
 * Reviewer: Rona                                                             *
 * Date: 23.03.2021                                                           *
 * Description: implementation of ThreadPool                                  *
 *                                                                            * 
 * Infinity Labs FS1123                                                       *
 ******************************************************************************/
package il.co.ilrd.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import il.co.ilrd.waitablepq.WaitablePriorityQueueCond;

public class ThreadPoolIMP implements Executor {
	public enum Priority {
		LOW,
		MEDIUM,
		HIGH;
	}
	
	private WaitablePriorityQueueCond<Task<?>> tasks;
	private List<Thread> threads;
	private final static int DEFAULT_PRIORITY = Priority.MEDIUM.ordinal();
	private final static int SYSTEM_PRIORITY = Priority.HIGH.ordinal() + 1;
	private final Semaphore semaStop = new Semaphore(0);
	private Semaphore semaFinished = null;
	
	public ThreadPoolIMP(int numOfThreads){
		if (numOfThreads < 0) {
			throw new IllegalArgumentException();
		}
		
		tasks = new WaitablePriorityQueueCond<>();
		threads = new ArrayList<>(numOfThreads);
		
		increaseNumOfThreads(numOfThreads);
	}
	
	@Override
	public void execute(Runnable command) {
		submit(command);
	}
	
	public <T> Future<T> submit(Callable<T> callable, Priority priority){
		Objects.requireNonNull(callable);
		Objects.requireNonNull(priority);
		return submitImp(callable, priority.ordinal());
	}
	
	public <T> Future<T> submit(Callable<T> callable){
		Objects.requireNonNull(callable);
		return submitImp(callable,  DEFAULT_PRIORITY);
	}
	
	public Future<Object> submit(Runnable runnable, Priority priority){
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(priority);
		return submitImp(Executors.callable(runnable), priority.ordinal());
	}
	public Future<Object> submit(Runnable runnable){
		Objects.requireNonNull(runnable);
		return submitImp(Executors.callable(runnable), DEFAULT_PRIORITY);
	}
	
	public <T> Future<T> submit(Runnable runnable, Priority priority, T result){
		Objects.requireNonNull(runnable);
		return submitImp(Executors.callable(runnable, result), priority.ordinal());
	}
	
	public void setNumberOfThreads(int numOfThreads){
		if (numOfThreads < 0) {
			throw new IllegalArgumentException();
		}
		
		
		int currNumOfThreads = threads.size();
		
		if(numOfThreads >= currNumOfThreads) {
			increaseNumOfThreads(numOfThreads - currNumOfThreads);
		}
		else {
			decreaseNumOfThreads(currNumOfThreads - numOfThreads, SYSTEM_PRIORITY);
		}
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("Num Of Threads is : " + threads.size());
	}
	
	public void pause(){
		for(int i = 0; i < threads.size(); i++) {
			submitImp(() -> {
				semaStop.acquire();
				return null;
			}, SYSTEM_PRIORITY);
		}
	}
	
	public void resume(){
		for(int i = 0; i < threads.size(); i++) {
			semaStop.release();
		}
	}
	
	public void shutdown(){
		semaFinished = new Semaphore(0);
		
		for(int i = 0; i < threads.size(); i++) {
			submitImp(() -> {
				ThreadImp threadToStop = (ThreadImp)Thread.currentThread();
				threadToStop.isRunning = false;
				threads.remove(threadToStop);
				
				if(threads.isEmpty()) {
					semaFinished.release();
				}
				return null;
			}, Priority.LOW.ordinal());
		}
	}
	
	public void awaitTermination() throws InterruptedException{
		semaFinished.acquire();
	}
	
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException{
		return semaFinished.tryAcquire(timeout, unit);
	}
	
	private void increaseNumOfThreads(int addThreads) {
		for(int i = 0; i < addThreads; i++) {
			ThreadImp newThread = new ThreadImp();
			threads.add(newThread);
			newThread.start();
		}
	}
	
	private void decreaseNumOfThreads(int removeThreads, int priority) {
		for(int i = 0; i < removeThreads; i++) {
			submitImp(() -> {
				ThreadImp threadToStop = (ThreadImp)Thread.currentThread();
				threadToStop.isRunning = false;
				threads.remove(threadToStop);
				
				return null;
			}, priority);
		}
	}
	
	private <T> Future<T> submitImp(Callable<T> callable, int priority){
		Task<T> task = new Task<>(callable, priority);
		tasks.enqueue(task); 
		
		return task.getFuture();
	}
	
	private boolean removeTask(Task<?> task){		
		return tasks.remove(task);
	}
	
	private class ThreadImp extends Thread{
		private boolean isRunning = true;
		@Override
		public void run() {
			while(isRunning){
				Task<?> task = tasks.dequeue();
				task.runningThread = Thread.currentThread();
				task.runTask();
			}
		}
	}
	
	private class Task<T> implements Comparable<Task<T>>{
		private final Callable<T> task;
		private final int priority;
		private final TaskFuture<T> taskFuture = new TaskFuture<T>();
		private Thread runningThread = null;
		private T retVal = null;
		private boolean isCancelled = false;
		private boolean isDone = false;
		private boolean isRealDone = false;
		private final Lock lock = new ReentrantLock();
		private final Condition cv  = lock.newCondition();
		
		public Task(Callable<T> task, int priority){
			this.task = task;
			this.priority = priority;
		}
		
		public void runTask(){  
			try {
				retVal = task.call();
				isDone = true;
				isRealDone = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			lock.lock();
			try {
				cv.signal();
			}
			finally {
				lock.unlock();
			}
			
		}
		
		public Future<T> getFuture(){
			return taskFuture;
		}
		
		@Override
		public int compareTo(Task<T> other) {
			return other.priority - priority;
		}
		
		private class TaskFuture<E> implements Future<E>{
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				isCancelled = removeTask(Task.this);
				
				if (!isDone() && !isCancelled() && mayInterruptIfRunning && null != runningThread){
					runningThread.interrupt();
					isCancelled = true;
				}
				isDone = true;
				
				return isCancelled();
			}
			
			@Override
			public boolean isCancelled() {
				return isCancelled;
			}
			
			@Override
			public boolean isDone() {
				return isDone;
			}
			
			@Override
			public E get() throws InterruptedException, ExecutionException {
				try {
					return get(Long.MAX_VALUE, TimeUnit.DAYS);
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
				
				return null;
			}
			
			@Override
			public E get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				lock.lock();
				try {
					if(!isRealDone) {
						if (isCancelled()) {
							throw new CancellationException("the task is cancelled");
						}
						else if(null != runningThread && runningThread.isInterrupted()) {
							throw new InterruptedException();
						}
						else if (false == cv.await(timeout, unit)) {
							throw new TimeoutException("waiting the task timeout");
						}
					}
				} finally {
					lock.unlock();
				}
				
				@SuppressWarnings("unchecked")
				E res = (E) retVal;
				
				return res;
			}
		}
	}
	
	
}
