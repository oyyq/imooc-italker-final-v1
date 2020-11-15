# imooc-italker-final-v1

1. 解决了PushedUserRepo & PushedGroupRepo 在消息到达时的注册
2. Session在APP退出重新进入, 从后台回到前台能够收到服务器的重新推送, 刷新未读消息数量

遗留问题:
1. 在发送消息过快时由于网络问题, 部分消息不能送达服务器端, 或者getui没有推送.
2. 有些消息在服务器端创建时间早, 然而被getui推送更晚

3. 关于群的一些操作: 添加群员 / 修改群员权限 / 删除群员  / 退群等操作没有测试

4. 没有考虑APP在后台长时间停留被系统杀死的情况, 解决方案是Activity / Fragment 的onSavedInstanceState方法, 将一部分数据存储下来, 
  这样APP回到前台时能够恢复数据, 拿到原来的状态
5. Activity与Fragment的通信, 创建新的Fragment时传入数据Bundle, Fragment.setArgument(bundle)没有考虑, 从这个角度应该重构  
  
