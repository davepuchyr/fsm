package metatype.fsm;

import junit.framework.TestCase;
import metatype.fsm.FSM.EventHandler;
import metatype.fsm.FSM.FsmEvent;
import metatype.fsm.FSM.FsmState;

public class FsmExampleTest extends TestCase {
    // defines the state events
    private enum Events implements FsmEvent<Events> { 
      MSG, 
      CLOSE;
  
      @Override
      public Events getType() {
        return this;
      } 
    };
    
    public void testExample() {
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
      
      fsm.deliver(Events.MSG);
      fsm.deliver(Events.MSG);
      fsm.deliver(Events.CLOSE);
      fsm.deliver(Events.MSG);
    }
}
