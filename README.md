# StateMachine

#### 介绍
Android状态机

#### 软件架构
输入事件状态机处理事件后回调出正确的状态

#### 使用说明

1.  自定义状态机，继承StateMachine
2.  自定义状态，继承State
3.  增加状态： addState(State state) 或者 addState(State state, State parentState)
4.  初始化状态： setInitialState(State state)
5.  启动状态机： start()
6.  退出状态机： quit()或者quitNow()
7.  状态处理逻辑： processMessage(Message msg)

参考FtpStateMachine
