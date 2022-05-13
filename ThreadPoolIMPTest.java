
package il.co.ilrd.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import il.co.ilrd.threadpool.ThreadPoolIMP.Priority;

public class ThreadPoolIMPTest {
	private static int counter = 0;
	public static void testBasicOperations() {
		System.out.println("testBasicOperations");
		ThreadPoolIMP threadPool = new ThreadPoolIMP(4);
		
		threadPool.submit(() -> {
			System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
			
			return null;
		});
		
		threadPool.submit(() -> {
			System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
			
			return null;
		}, Priority.LOW);
		
		threadPool.submit(() -> {
			System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
		}, Priority.HIGH);
		
		threadPool.submit(() -> {
			System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
		}); 
		
		threadPool.execute(() -> {
			System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
		}); 
	}
	
	public static void testFuture() {
        int tasksNumber = 10;
        counter = 0;
        List<Future<Integer>> futureTasks = new ArrayList<>(tasksNumber);
        ThreadPoolIMP threadPool = new ThreadPoolIMP(3);
        
        for(int i = 0; i < tasksNumber; i++) {
            futureTasks.add(threadPool.submit(() -> {
                System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
                Thread.sleep(500);
                return ++counter;
            }));
        }
        
        Future<Integer> lastFuture = futureTasks.get(tasksNumber - 1);
        Future<Integer> startFuture = futureTasks.get(0);

        try {
            System.out.println("startFuture get Value is :  " + startFuture.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        
        System.out.println("Cancelling " + lastFuture.cancel(false));
        System.out.println("lastFuture Done " + lastFuture.isDone());
        System.out.println("canceled " + lastFuture.isCancelled());
        try {
            System.out.println("lastFuture get Value is :  " + lastFuture.get());
        }catch (InterruptedException e) {
            e.printStackTrace();
        }catch (ExecutionException e) {
            System.out.println("Got ExecutionException ");
            e.printStackTrace();
        }catch (CancellationException e) {
            System.out.println("Got CancellationException ");
            e.printStackTrace();
        }
    }
	
	public static void testNumThreads() {
		ThreadPoolIMP threadPool = new ThreadPoolIMP(3);
		threadPool.setNumberOfThreads(2);
		threadPool.setNumberOfThreads(8);
		threadPool.setNumberOfThreads(8);
		threadPool.setNumberOfThreads(10);
		threadPool.setNumberOfThreads(1);
    }
	
	public static void testStopResume() {
		ThreadPoolIMP threadPool = new ThreadPoolIMP(4);
		int tasksNumber = 10;
		
		for(int i = 0; i < tasksNumber; i++) {
			threadPool.submit(() -> {
				System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
		}
		
		System.out.println("pausing To 5 second");
		threadPool.pause();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("resuming....");
		threadPool.resume();
		
    }
	
	public static void testShutDown() {
		ThreadPoolIMP threadPool = new ThreadPoolIMP(4);
		int tasksNumber = 10;
		
		for(int i = 0; i < tasksNumber; i++) {
			threadPool.submit(() -> {
				System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
		}
		threadPool.shutdown();
		
    }
	
	public static void testAwait() throws InterruptedException {
		ThreadPoolIMP threadPool = new ThreadPoolIMP(4);
		int tasksNumber = 10;
		
		for(int i = 0; i < tasksNumber; i++) {
			threadPool.submit(() -> {
				System.out.println("Counter is " + counter++ +" The Thread is " + Thread.currentThread().getName());
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
		}
		threadPool.shutdown();
		threadPool.awaitTermination();
		
    }
	
	public static void main(String[] args) throws InterruptedException {
		//testBasicOperations();
		//testFuture();
		//testNumThreads();
		//testStopResume();
		//testShutDown();
		testAwait();
	}
}