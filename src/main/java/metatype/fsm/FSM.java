package metatype.fsm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines and manages a finite state machine.
 * 
 * @author metatype
 *
 * @param <E> the event type
 */
public class FSM<E extends Enum<E>> {
  /**
   * Defines a response to event delivered either to a state or via a state
   * transition. 
   *
   * @param <E> the event type
   */
  public interface EventHandler<E extends Enum<E>> {
    void handleEvent(FsmEvent<E> evt);
  }
  
  /**
   * Defines a guard condition applied to a state transition.
   *
   * @param <E> the event type
   */
  public interface Guard<E extends Enum<E>> {
    /**
     * Returns true if the guard condition passes and the transition is allowed.
     * @param evt the event
     * @return true if allowed
     */
    boolean accept(FsmEvent<E> evt);
  }

  /**
   * Defines an event to be delivered to a state machine.
   * @param <E> the event type
   */
  public interface FsmEvent<E extends Enum<E>> {
    /**
     * Defines the event type
     * @return the type
     */
    E getType();
  }
  
  /**
   * Provides a default event implementation.
   *
   * @param <E> the event type
   */
  public static class FsmEventImpl<E extends Enum<E>> implements FsmEvent<E> {
    /** the event type */
    private final E type;

    public FsmEventImpl(E type) {
      this.type = type;
    }
    
    @Override
    public E getType() {
      return type;
    }
    
    @Override
    public String toString() {
      return type.name();
    }
  }
  
  public static class Transition<E extends Enum<E>> {
    /** the source state */
    private final FsmState<E> from;
    
    /** the target state */
    private final FsmState<E> to;
    
    /** the transition trigger, optional */
    private final E trigger;
    
    /** the guard condition, optional */
    private final Guard<E> guard;
    
    /** the event action, optional */
    private final EventHandler<E> handler;
    
    private Transition(FsmState<E> from, FsmState<E> to, E trigger, Guard<E> guard, EventHandler<E> handler) {
      assert from != null;
      assert to != null;
      
      this.from = from;
      this.to = to;
      this.trigger = trigger;
      this.guard = guard;
      this.handler = handler;
    }
    
    @Override
    public String toString() {
      return String.format("%s ----- %s [%s] / %s -----> %s", from, trigger, guard, handler, to);
    }
  }
  
  public static class FsmState<E extends Enum<E>> {
    /** the state name */
    private final String name;
    
    /** the entry action, optional */
    private final Runnable entry;
    
    /** the exit action, optional */
    private final Runnable exit;
    
    /** the default event handler, optional */
    private final EventHandler<E> defHandler;
    
    /** the ordered list of outbound transitions */
    private final List<Transition<E>> transitions;
    
    /** the internal event handlers */
    private final EnumMap<E, EventHandler<E>> handlers;
    
    public FsmState(String name, Class<E> types) {
      this(name, types, null, null, null);
    }
    
    public FsmState(String name, Class<E> types, Runnable entry, Runnable exit, EventHandler<E> defHandler) {
      this.name = name;
      this.entry = entry;
      this.exit = exit;
      this.defHandler = defHandler;
      
      transitions = new ArrayList<Transition<E>>();
      handlers = new EnumMap<E, EventHandler<E>>(types);
    }

    /**
     * Returns the state name.
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Adds a transition to a new state.
     * 
     * @param to the destination state
     * @param trigger the event trigger, optional
     * @return the state
     */
    public FsmState<E> addTransition(FsmState<E> to, E trigger) {
      return addTransition(to, trigger, null, null);
    }

    /**
     * Adds a transition to a new state.
     * 
     * @param to the destination state
     * @param trigger the event trigger, optional
     * @param guard the guard condition, optional
     * @param handler the event action, optional
     * @return the state
     */
    public FsmState<E> addTransition(FsmState<E> to, E trigger, Guard<E> guard, EventHandler<E> handler) {
      assert to != null;
      
      if (this == to && guard == null) {
        throw new IllegalArgumentException("Unguarded self transition will cause an non-terminating loop");
      }
      
      transitions.add(new Transition<E>(this, to, trigger, guard, handler));
      return this;
    }

    /**
     * Adds an event handler.
     * 
     * @param trigger the event trigger
     * @param eh the event action
     * @return the state
     */
    public FsmState<E> addHandler(E trigger, EventHandler<E> eh) {
      assert trigger != null;
      assert eh != null;
      
      handlers.put(trigger, eh);
      return this;
    }

    /**
     * Returns the ordered list of state transitions.  Transitions are selected
     * by finding the first match according to the event type and guard
     * condition.
     * 
     * @return the transitions
     */
    public Iterable<Transition<E>> getTransitions() {
      return Collections.unmodifiableCollection(transitions);
    }

    /**
     * Returns the event handlers.  The appropriate handler is invoked by matching
     * the trigger type when an event is delivered to a state.
     * 
     * @see FsmEvent#getType()
     * @return the handlers
     */
    public Iterable<Entry<E, EventHandler<E>>> getHandlers() {
      return Collections.unmodifiableMap(handlers).entrySet();
    }

    /**
     * Returns the default event handler.  This action is invoked when an event
     * is delivered to a state and no matching event handlers are found.
     * 
     * @return the default handler
     */
    public EventHandler<E> getDefaultHandler() {
      return defHandler;
    }
    
    @Override
    public String toString() {
      return name;
    }
    
    private FsmState<E> handleEvent(FsmEvent<E> evt) {
      EventHandler<E> eh = handlers.containsKey(evt.getType()) ?
          handlers.get(evt.getType()) : defHandler;
      if (eh != null) {
        eh.handleEvent(evt);
      }
      
      return transition(this, evt);
    }

    private FsmState<E> transition(FsmState<E> state, FsmEvent<E> evt) {
      for (Transition<E> t : state.transitions) {
        if (t.trigger == null || t.trigger == evt.getType()) {
          if (t.guard == null || t.guard.accept(evt)) {
            LOG.trace("Invoking transition {}", t);
            if (state.exit != null) {
              state.exit.run();
            }

            if (t.handler != null) {
              t.handler.handleEvent(evt);
            }
            
            if (t.to.entry != null) {
              t.to.entry.run();
            }

            return transition(t.to, evt);
          }
        }
      }
      return state;
    }
  }

  /**
   * Composes two guard conditions using short-circuit logical AND.
   * 
   * @param l the left condition
   * @param r the right condition
   * @return the composed condition
   */
  public static <E extends Enum<E>> Guard<E> and(final Guard<E> l, final Guard<E> r) {
    return new Guard<E>() {
      @Override
      public boolean accept(FsmEvent<E> evt) {
        return l.accept(evt) && r.accept(evt);
      }
    };
  }
  
  /**
   * Composes two guard conditions using short-circuit logical OR.
   * 
   * @param l the left condition
   * @param r the right condition
   * @return the composed condition
   */
  public static <E extends Enum<E>> Guard<E> or(final Guard<E> l, final Guard<E> r) {
    return new Guard<E>() {
      @Override
      public boolean accept(FsmEvent<E> evt) {
        return l.accept(evt) || r.accept(evt);
      }
    };
  }
  
  /**
   * Negates a guard condition using logical NOT.
   *    * @param g the guard condition
   * @return the negated condition
   */
  public static <E extends Enum<E>> Guard<E> not(final Guard<E> g) {
    return new Guard<E>() {
      @Override
      public boolean accept(FsmEvent<E> evt) {
        return !g.accept(evt);
      }
    };
  }

  /**
   * Composes two guard conditions using logical XOR.
   * 
   * @param l the left condition
   * @param r the right condition
   * @return the composed condition
   */
  public static <E extends Enum<E>> Guard<E> xor(final Guard<E> l, final Guard<E> r) {
    return new Guard<E>() {
      @Override
      public boolean accept(FsmEvent<E> evt) {
        return l.accept(evt) ^ r.accept(evt);
      }
    };
  }

  /** the logger */
  private static final Logger LOG = LoggerFactory.getLogger(FSM.class);
  
  /** events waiting to be delivered */
  private final BlockingQueue<FsmEvent<E>> events;
  
  /** true if a thread is delivering an event */
  private final AtomicBoolean running;
  
  /** the current state */
  private FsmState<E> current;

  public FSM(FsmState<E> start) {
    this(start, new LinkedBlockingQueue<FsmEvent<E>>());
  }
  
  public FSM(FsmState<E> start, BlockingQueue<FsmEvent<E>> queue) {
    this.events = queue;
    running = new AtomicBoolean(false);
    current = start;
    
    if (current.entry != null) {
      current.entry.run();
    }
  }
  
  /**
   * Returns the current state.  May block if threads are delivering events.
   * @return the current state
   */
  public FsmState<E> getCurrent() {
    synchronized (this) {
      return current;
    }
  }
  
  /**
   * Delivers an event to the state machine.  If the state machine is currently
   * busy, the event will be queued and delivered in FIFO order.
   * 
   * @param evt the event to deliver
   */
  public void deliver(FsmEvent<E> evt) {
    events.add(evt);
    if (!running.compareAndSet(false, true)) {
      LOG.trace("Queued event {} for later delivery", evt);
      return;
    }
    
    try {
      synchronized (this) {
        while (!events.isEmpty()) {
          FsmEvent<E> next = events.take();
          LOG.trace("Delivering event {} to state {}", next, current);
          
          current = current.handleEvent(next);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      
    } finally {
      running.set(false);
    }
  }
}
