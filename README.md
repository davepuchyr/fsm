fsm
===

Provides a Finite State Machine utility for improved state management.  The state 
machine model follows the definition from the Unified Modeling Language (UML).  The 
following elements are exposed in the API:

**Events** are external stimuli delvered to the state machine.  The event consists
of a type and optional user-defined parameters that can be acted upon.  Each event
delivered to the state machine is processed fully (run-to-completion) before the
next event is evaluated.  If an event arrives while the state machine is busy, the
event is queued for later delivery.  The currently processing thread will continue
consuming events until the queue is empty.

**States** define a logical representation of the allowed conditions within a
system or component.  States may define entry and exit handlers, a set of 
internal event handlers, and a default event handler.  The state machine
always has a single active state at any point in time.

**Transitions** link states together.  Transitions are triggered by the arrival
of an event.  A single event may cause a cascade of state transitions.  A
transition is selected by matching the event type to the transition trigger.  If
the trigger matches (or is unspecified) the guard condition is tested.  If the
guard condition passes the transition is performed.  During event transition,
the following callbacks are invoked:

1. Source state exit
1. Transition event handler
1. Destination state entry handler

Once the destination state has been entered it's transitions are evaluated to
determine if any further transitions are required.  Transitions to self should
be guarded to avoid inifinite recursion loops.

The state machine approach allows a clean separation of internal state from external
events.  As a component grows in features and complexity, typically internal flags
will be added to control state.  However, these flags can be cumbersome maintain and
add complexity that is not material to the core feature set.  Encapsulating this state
within a state machine simplifies management and maintenance.  For example, a component
can define a _CLOSING_ state.  If the _Close_ event is delivered to that state it can be
safely ignored.  For a more complicated example, consider the act of establishing a
session that requires communicating with several external systems.  The messages are
sent using non-blocking messages to avoid consuming a thread.  The timeouts and stages
must be carefully tracked to avoid leaking resources.

    INITIALIZING --> SYSTEM1 --> SYSTEM2 --> SYSTEM3 --> CLOSING --> END
                        |           |           |          ^ 
                        |           |           v          |
                        +-----------+------> ABORTING -----+

In addition, a state machine may be nested within a composite parent state.  This
allows common behaviors in the substate machine to be factored out into the parent
state.

For more information on UML state machines, see http://en.wikipedia.org/wiki/UML_state_machine.

## Usage

The first step is to define and construct the state machine.

    // defines the state events
    private enum Events implements FsmEvent<Events> { 
      MSG, 
      CLOSE;
  
      @Override
      public Events getType() {
        return this;
      } 
    };
    
    // defines an echo state that prints messages to the console
    FsmState<Events> echo = new FsmState<Events>("echo", Events.class);
    echo.addHandler(Events.MSG, new EventHandler<Events>() {
      private int counter;
      
      @Override
      public void handleEvent(FsmEvent<Events> evt) {
        System.out.printf("%s %s\n", evt, counter++);
      }
    });

    // defines a closed state that logs warnings from the default handler
    FsmState<Events> closed = new FsmState<Events>("closed", Events.class, null, null, new EventHandler<Events>() {
      @Override
      public void handleEvent(FsmEvent<Events> evt) {
        System.out.printf("Ignoring %s as the state machine is closed\n", evt);
      }
    });
    
    // creates the state machine
    FSM<Events> fsm = new FSM<Events>(echo);
    
    // adds a state transition to the closed state
    echo.addTransition(closed, Events.CLOSE);

Next, send the events to the state machine.

    fsm.deliver(Events.MSG);
    fsm.deliver(Events.MSG);
    fsm.deliver(Events.CLOSE);
    fsm.deliver(Events.MSG);

This will print the following to the console:

    "MSG 1"
    "MSG 2"
    "Ignoring MSG as the state machine is closed"
 
