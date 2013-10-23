package metatype.fsm;

import junit.framework.TestCase;
import metatype.fsm.FSM.EventHandler;
import metatype.fsm.FSM.FsmEvent;
import metatype.fsm.FSM.FsmState;
import metatype.fsm.FSM.Guard;

public class FSMTest extends TestCase {
  FsmState<Events> s1;
  FsmState<Events> s2;
  FsmState<Events> s3;

  TestCallback entry1;
  TestCallback exit1;
  TestCallback entry2;
  TestCallback exit2;
  
  private static class TestCallback implements Runnable, EventHandler<Events>, Guard<Events> {
    private boolean pass;
    private boolean hit;
    
    @Override
    public void run() {
      hit = true;
    }

    @Override
    public boolean accept(FsmEvent<Events> evt) {
      hit = true;
      return pass;
    }

    @Override
    public void handleEvent(FsmEvent<Events> evt) {
      hit = true;
    }
  }
  
  private enum Events implements FsmEvent<Events> {
    A, B, C, D, E, F;

    @Override
    public Events getType() {
      return this;
    }
  }
  
  public void testHandler() {
    FsmState<Events> s = new FsmState<Events>("s", Events.class);

    TestCallback th = new TestCallback();
    s.addHandler(Events.A, th);
    
    FSM<Events> fsm = new FSM<Events>(s);
    fsm.deliver(Events.A);
    assertTrue(th.hit);
  }
  
  public void testDefault() {
    TestCallback th = new TestCallback();
    FSM<Events> fsm = new FSM<Events>(new FsmState<Events>("me", Events.class, null, null, th));
    
    fsm.deliver(Events.A);
    assertTrue(th.hit);
  }
  
  public void testTransition() {
    TestCallback th = new TestCallback();
    s1.addTransition(s2, Events.A, null, th);
    s1.addTransition(s2, Events.B);
    s1.addTransition(s3, Events.C);
    FSM<Events> fsm = new FSM<Events>(s1);

    fsm.deliver(Events.A);
    assertEquals(s2, fsm.getCurrent());
    assertTrue(entry1.hit);
    assertTrue(exit1.hit);
    assertTrue(entry2.hit);
    assertTrue(th.hit);
  }

  public void testSelfTransition() {
    final TestCallback tc = new TestCallback();
    tc.pass = true;
    
    s1.addTransition(s1, Events.A, tc, new EventHandler<Events>() {
      @Override
      public void handleEvent(FsmEvent<Events> evt) {
        tc.pass = false;
      }
    });
    
    FSM<Events> fsm = new FSM<Events>(s1);
    fsm.deliver(Events.A);
    assertEquals(s1, fsm.getCurrent());
    assertTrue(entry1.hit);
    assertTrue(exit1.hit);
  }

  public void testTransitionGuard() {
    TestCallback tg = new TestCallback();
    
    s1.addTransition(s2, Events.A, tg, null);
    FSM<Events> fsm = new FSM<Events>(s1);
    
    fsm.deliver(Events.A);
    assertEquals(s1, fsm.getCurrent());
    assertTrue(tg.hit);
    
    tg.pass = true;
    tg.hit = false;

    fsm.deliver(Events.A);
    assertEquals(s2, fsm.getCurrent());
    assertTrue(tg.hit);
  }
  
  public void testTransitionTriggerless() {
    s1.addTransition(s2, null);
    s2.addTransition(s3, null);
    FSM<Events> fsm = new FSM<Events>(s1);
    
    fsm.deliver(Events.A);
    assertEquals(s3, fsm.getCurrent());
  }

  public void testMoreEvents() {
    final FSM<Events> fsm = new FSM<Events>(s1);
    final TestCallback a = new TestCallback();
    
    s1.addHandler(Events.A, a);
    s1.addHandler(Events.B, new EventHandler<Events>() {
      @Override
      public void handleEvent(FsmEvent<Events> evt) {
        fsm.deliver(Events.A);
      }
    });
    
    fsm.deliver(Events.B);
    assertTrue(a.hit);
  }

  public void setUp() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
    
    entry1 = new TestCallback();
    exit1 = new TestCallback();
    entry2 = new TestCallback();
    exit2 = new TestCallback();

    s1 = new FsmState<Events>("s1", Events.class, entry1, exit1, null);
    s2 = new FsmState<Events>("s2", Events.class, entry2, exit2, null);
    s3 = new FsmState<Events>("s3", Events.class);
  }
}
