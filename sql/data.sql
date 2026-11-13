-- ============================================================
-- AI 考试系统 种子数据
-- 管理员账号均为: 用户名/123456
--   admin    项目管理部招聘负责人(PM_ADMIN)
--   zhangsan 研发部招聘负责人(DEPT_ADMIN)
--   lisi     数据部招聘负责人(DEPT_ADMIN)
--   wangwu   测试部招聘负责人(DEPT_ADMIN)
-- ============================================================
USE exam_ai;

INSERT INTO dept (id, name, code, created_at) VALUES
(1, '项目管理部', 'PMO', NOW()),
(2, '研发部', 'RD', NOW()),
(3, '数据部', 'DATA', NOW()),
(4, '测试部', 'QA', NOW());

INSERT INTO sys_user (id, username, password, real_name, role, dept_id, email, phone, status, created_at, updated_at) VALUES
(1, 'admin', '$2a$10$Z4a/TJW0XlnDHA15hZ1kG.705nFpaLP5Ni4QMGt4z/7EuO8pJRfSG', '王管理', 'PM_ADMIN', 1, 'admin@exam.com', '13800000001', 1, NOW(), NOW()),
(2, 'zhangsan', '$2a$10$Z4a/TJW0XlnDHA15hZ1kG.705nFpaLP5Ni4QMGt4z/7EuO8pJRfSG', '张三', 'DEPT_ADMIN', 2, 'zhangsan@exam.com', '13800000002', 1, NOW(), NOW()),
(3, 'lisi', '$2a$10$Z4a/TJW0XlnDHA15hZ1kG.705nFpaLP5Ni4QMGt4z/7EuO8pJRfSG', '李四', 'DEPT_ADMIN', 3, 'lisi@exam.com', '13800000003', 1, NOW(), NOW()),
(4, 'wangwu', '$2a$10$Z4a/TJW0XlnDHA15hZ1kG.705nFpaLP5Ni4QMGt4z/7EuO8pJRfSG', '王五', 'DEPT_ADMIN', 4, 'wangwu@exam.com', '13800000004', 1, NOW(), NOW());

-- ---------------- 公共题库 ----------------
INSERT INTO question (bank_type, dept_id, type, content, options_json, answer, analysis, score_points, skill, difficulty, knowledge_point, default_score, status, creator_id, created_at, updated_at) VALUES
('PUBLIC', NULL, 'SINGLE', 'Java 中用于定义常量的关键字是？', '["static","final","const","define"]', 'B', 'final 修饰的变量一旦赋值不可更改，即常量。const 是保留字但未使用。', NULL, 'Java', 1, '基础语法', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'Java 中 int 类型成员变量的默认值是？', '["null","0","-1","未定义"]', 'B', '成员变量有默认值，int 默认为 0；局部变量必须显式初始化。', NULL, 'Java', 1, '基础语法', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '下列哪个集合类是线程安全的？', '["ArrayList","HashMap","ConcurrentHashMap","LinkedList"]', 'C', 'ConcurrentHashMap 通过分段锁/CAS 保证线程安全，其余均非线程安全。', NULL, 'Java', 3, '集合', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '"abcdef".substring(1, 3) 的结果是？', '["abc","bc","bcd","abcd"]', 'B', 'substring 含头不含尾，取下标 1、2 的字符。', NULL, 'Java', 2, '字符串', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '二分查找的时间复杂度是？', '["O(n)","O(log n)","O(n log n)","O(1)"]', 'B', '每次将搜索范围减半，故为 O(log n)，前提是有序。', NULL, 'Java', 3, '查找', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '关于 finally 块，下列说法正确的是？', '["发生异常时不执行","只有无异常时执行","无论是否异常通常都会执行","可以替代 catch"]', 'C', 'finally 通常总会执行，常用于资源释放；System.exit 等极端情况除外。', NULL, 'Java', 2, '异常处理', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'Integer a = 127, b = 127; Integer c = 128, d = 128; a==b 与 c==d 的结果分别是？', '["true, true","true, false","false, true","false, false"]', 'B', 'Integer 缓存 -128~127，区间内复用对象；128 超出缓存新建对象，== 比较引用。', NULL, 'Java', 5, '基础语法', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '下列哪项不是面向对象的基本特征？', '["封装","继承","多态","编译"]', 'D', '面向对象三大特征：封装、继承、多态。', NULL, 'Java', 1, '面向对象', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '面向对象的基本特征包括？', '["封装","继承","多态","递归"]', 'ABC', '封装、继承、多态是面向对象三大特征，递归是一种算法思想。', NULL, 'Java', 2, '面向对象', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于 ArrayList 与 LinkedList，正确的有？', '["ArrayList 基于动态数组","LinkedList 基于双向链表","ArrayList 随机访问更快","LinkedList 随机访问更快"]', 'ABC', 'ArrayList 数组实现支持 O(1) 随机访问；LinkedList 链表实现访问需 O(n)。', NULL, 'Java', 3, '集合', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', 'Java 中创建线程的方式包括？', '["继承 Thread 类","实现 Runnable 接口","实现 Callable 接口配合 FutureTask","使用 synchronized 关键字"]', 'ABC', 'synchronized 是同步手段而非创建线程方式。', NULL, 'Java', 3, '多线程', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于 JVM 内存区域，正确的有？', '["堆是线程共享的","虚拟机栈是线程私有的","方法区存放类信息","程序计数器是线程共享的"]', 'ABC', '程序计数器线程私有；堆与方法区线程共享。', NULL, 'Java', 5, 'JVM', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'Java 支持类的多继承。', '["正确","错误"]', 'B', 'Java 类只支持单继承，接口可以多实现。', NULL, 'Java', 1, '面向对象', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'String 对象一旦创建其内容不可改变。', '["正确","错误"]', 'A', 'String 不可变，拼接等操作会生成新对象。', NULL, 'Java', 2, '字符串', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'HashMap 允许 key 为 null。', '["正确","错误"]', 'A', 'HashMap 允许一个 null key，Hashtable 不允许。', NULL, 'Java', 3, '集合', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', '== 与 equals 的比较结果永远相同。', '["正确","错误"]', 'B', '== 比较引用（基本类型比值），equals 默认同 == 但可被重写为内容比较。', NULL, 'Java', 2, '基础语法', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '简述 HashMap 与 Hashtable 的区别。', NULL, 'HashMap 线程不安全、允许 null 键值、效率较高；Hashtable 线程安全（方法加 synchronized）、不允许 null、效率较低。并发场景推荐 ConcurrentHashMap。', 'HashMap 非同步，Hashtable 同步；null 支持差异；性能差异。', '1.线程安全性：HashMap 非线程安全，Hashtable 线程安全；2.null 支持：HashMap 允许 null key/value，Hashtable 不允许；3.性能：Hashtable 同步开销大，并发推荐 ConcurrentHashMap；4.继承与历史：Hashtable 继承 Dictionary，HashMap 实现 Map 接口。', 'Java', 3, '集合', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '简述 JVM 垃圾回收的基本机制。', NULL, 'JVM 通过可达性分析判断对象存活，GC Roots 不可达的对象可被回收。堆分代管理：新生代用复制算法（Minor GC），老年代用标记-清除/标记-整理（Major/Full GC）。常见收集器有 Serial、Parallel、CMS、G1。', '可达性分析；分代回收；常见算法与收集器。', '1.对象存活判定：引用计数/可达性分析，GC Roots；2.分代收集：新生代复制算法、老年代标记清除或标记整理；3.常见收集器：Serial/Parallel/CMS/G1 任一说明；4.GC 类型：Minor GC 与 Full GC 触发时机。', 'Java', 5, 'JVM', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', 'ArrayList 和 LinkedList 的区别及各自适用场景？', NULL, 'ArrayList 基于动态数组，随机访问 O(1)，尾部增删摊还 O(1)，中间增删需移动元素；LinkedList 基于双向链表，头尾增删 O(1)，随机访问 O(n)。读多写少用 ArrayList，频繁头尾插入删除用 LinkedList。', '底层结构；复杂度差异；场景选择。', '1.底层结构：动态数组 vs 双向链表；2.随机访问复杂度 O(1) vs O(n)；3.增删复杂度差异与元素移动；4.场景：读多写少选 ArrayList，频繁头尾增删选 LinkedList。', 'Java', 2, '集合', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '64 位系统中 C++ 指针变量占用多少字节？', '["4","8","16","取决于指向类型"]', 'B', '64 位系统地址总线 64 位，指针占 8 字节，与指向类型无关。', NULL, 'C++', 3, '指针', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'C++ 中 virtual 关键字的主要作用是？', '["定义常量","实现运行时多态","声明内联函数","禁止继承"]', 'B', '虚函数通过虚函数表实现运行时多态（动态绑定）。', NULL, 'C++', 3, '面向对象', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '使用 std::vector 需要包含哪个头文件？', '["<array>","<vector>","<list>","<map>"]', 'B', 'std::vector 定义于 <vector>。', NULL, 'C++', 1, 'STL', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '关于 C++ 引用，下列说法错误的是？', '["引用必须初始化","引用是变量的别名","引用可以重新绑定到其他变量","引用不占用额外内存语义上"]', 'C', '引用一经绑定不可更改，指针可以重新赋值。', NULL, 'C++', 4, '指针', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于 C++ 引用，正确的有？', '["声明时必须初始化","不能绑定到临时变量的非常量引用","可以作为函数参数避免拷贝","可以声明引用的数组"]', 'ABC', '引用数组非法；非常量左值引用不能绑定临时对象。', NULL, 'C++', 3, '指针', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于 C++ 构造函数，正确的有？', '["可以重载","可以设置默认参数","名字与类名相同","可以声明为 virtual"]', 'ABC', '构造函数不能是虚函数；析构函数可以是虚函数。', NULL, 'C++', 3, '面向对象', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'C++ 支持运算符重载。', '["正确","错误"]', 'A', 'C++ 允许重载大多数运算符，如 +、<< 等。', NULL, 'C++', 2, '基础语法', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'C++ 析构函数可以重载。', '["正确","错误"]', 'B', '析构函数无参数，不能重载，一个类只有一个析构函数。', NULL, 'C++', 3, '面向对象', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '简述 C++ 深拷贝与浅拷贝的区别。', NULL, '浅拷贝只复制指针值，两个对象共享同一块堆内存，析构时可能重复释放；深拷贝为新对象分配独立内存并复制内容，互不影响。含指针成员的类应自定义拷贝构造与赋值运算符实现深拷贝。', '浅拷贝共享内存；深拷贝独立内存；重复释放风险；三法则。', '1.浅拷贝：仅复制指针，共享内存；2.深拷贝：分配新内存并复制内容；3.风险：浅拷贝导致重复 delete/悬空指针；4.实践：自定义拷贝构造、赋值运算符（三/五法则）。', 'C++', 3, '内存管理', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'SQL 中用于去除重复行的关键字是？', '["UNIQUE","DISTINCT","GROUP","DIFFERENT"]', 'B', 'SELECT DISTINCT 去除结果重复行。', NULL, '数据库', 1, 'SQL', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'MySQL InnoDB 默认的事务隔离级别是？', '["读未提交","读已提交","可重复读","串行化"]', 'C', 'InnoDB 默认 REPEATABLE READ，并通过 MVCC+间隙锁 largely 避免幻读。', NULL, '数据库', 3, '事务', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '数据库索引的主要作用是？', '["保证数据唯一","加快查询速度","减少存储空间","自动备份数据"]', 'B', '索引以额外存储与写开销换取查询加速。', NULL, '数据库', 2, '索引', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', 'LEFT JOIN 的含义是？', '["只返回两表匹配行","返回左表全部行及右表匹配行","返回右表全部行","返回两表笛卡尔积"]', 'B', '左连接保留左表全部记录，右表无匹配则补 NULL。', NULL, '数据库', 2, 'SQL', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '消除非主属性对码的传递依赖达到第几范式？', '["1NF","2NF","3NF","BCNF"]', 'C', '3NF 要求非主属性不传递依赖于码。', NULL, '数据库', 5, '范式', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '事务的 ACID 特性包括？', '["原子性","一致性","隔离性","持久性"]', 'ABCD', 'Atomicity、Consistency、Isolation、Durability。', NULL, '数据库', 3, '事务', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于数据库索引，正确的有？', '["索引会加快查询","索引会降低写入性能","索引越多越好","B+树是常见索引结构"]', 'ABD', '索引并非越多越好，过多索引增加写开销与存储。', NULL, '数据库', 3, '索引', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', '主键列允许为 NULL。', '["正确","错误"]', 'B', '主键要求非空且唯一。', NULL, '数据库', 1, '约束', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', 'DELETE 语句在事务中可以回滚，TRUNCATE 通常不可回滚。', '["正确","错误"]', 'A', 'DELETE 是 DML 记录日志可回滚；TRUNCATE 是 DDL，MySQL 中隐式提交。', NULL, '数据库', 3, 'SQL', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '简述数据库索引的作用与代价。', NULL, '索引通过 B+树等结构加速查询、排序与连接，避免全表扫描；代价是占用额外存储、降低增删改性能（需维护索引）。应在高频查询条件列上合理建索引，避免冗余索引。', '加速查询原理；存储与写开销；合理建索引原则。', '1.作用：加速查询/排序/连接，避免全表扫描；2.结构：B+树/哈希任一说明；3.代价：额外存储、写操作维护开销；4.原则：高频查询列建索引、避免过多冗余索引。', '数据库', 3, '索引', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '什么是事务？简述 ACID 四个特性。', NULL, '事务是一组不可分割的 SQL 操作序列。原子性：全部成功或全部回滚；一致性：事务前后数据满足约束；隔离性：并发事务互不干扰；持久性：提交后永久保存。', '事务定义；A/C/I/D 各特性解释。', '1.事务定义：不可分割的操作单元；2.原子性：全做或全不做；3.一致性：约束不被破坏；4.隔离性：并发互不干扰；5.持久性：提交后永久生效。', '数据库', 2, '事务', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '数组按下标随机访问元素的时间复杂度是？', '["O(1)","O(n)","O(log n)","O(n^2)"]', 'A', '数组内存连续，基址+偏移直接定位。', NULL, '数据结构', 1, '数组', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '单链表在已知头结点的情况下头部插入的时间复杂度是？', '["O(1)","O(n)","O(log n)","O(n^2)"]', 'A', '只需修改头指针与新结点 next。', NULL, '数据结构', 2, '链表', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '快速排序的平均时间复杂度是？', '["O(n)","O(n log n)","O(n^2)","O(log n)"]', 'B', '平均 O(n log n)，最坏（已有序且基准不当）O(n^2)。', NULL, '数据结构', 3, '排序', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'SINGLE', '下列哪种排序算法是稳定的？', '["快速排序","堆排序","归并排序","选择排序"]', 'C', '归并排序相等元素相对顺序不变；快排、堆排、选择排序不稳定。', NULL, '数据结构', 3, '排序', 3, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'MULTI', '关于栈和队列，正确的有？', '["栈是后进先出","队列是先进先出","栈可用于函数调用管理","队列只能顺序存储"]', 'ABC', '队列也可链式存储。', NULL, '数据结构', 2, '栈与队列', 4, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'JUDGE', '二分查找要求线性表有序且支持随机访问。', '["正确","错误"]', 'A', '二分查找依赖有序性与 O(1) 定位，链表不适用。', NULL, '数据结构', 2, '查找', 2, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '简述快速排序的基本思想与时间复杂度。', NULL, '选取基准元素，将序列划分为小于基准与大于基准的两部分（分区），递归对两部分排序。平均 O(n log n)，最坏 O(n^2)，可通过随机基准/三数取中优化；空间复杂度 O(log n) 递归栈。', '分治思想；分区过程；平均/最坏复杂度；优化手段。', '1.分治思想：选基准、分区、递归；2.分区过程描述；3.平均 O(n log n)、最坏 O(n^2)；4.优化：随机基准/三数取中/小数组插入排序任一。', '数据结构', 3, '排序', 10, 'NORMAL', 1, NOW(), NOW()),
('PUBLIC', NULL, 'ESSAY', '如何判断单链表是否存在环？说明思路与复杂度。', NULL, '快慢指针法：慢指针每次走一步，快指针每次走两步，若相遇则有环，快指针到达 null 则无环。时间 O(n)，空间 O(1)。也可用哈希表记录访问过的结点，时间 O(n) 空间 O(n)。', '快慢指针思路；相遇判定；复杂度；替代方案。', '1.快慢指针：慢走一步快走两步；2.判定：相遇有环，快到 null 无环；3.复杂度：时间 O(n) 空间 O(1)；4.替代：哈希集合法及其空间开销。', '数据结构', 3, '链表', 10, 'NORMAL', 1, NOW(), NOW());

-- ---------------- 研发部私有题库 ----------------
INSERT INTO question (bank_type, dept_id, type, content, options_json, answer, analysis, score_points, skill, difficulty, knowledge_point, default_score, status, creator_id, created_at, updated_at) VALUES
('PRIVATE', 2, 'SINGLE', 'Spring 中 Bean 的默认作用域是？', '["prototype","singleton","request","session"]', 'B', 'Spring 默认 singleton，每个容器一个实例。', NULL, 'Java', 2, 'Spring', 3, 'NORMAL', 2, NOW(), NOW()),
('PRIVATE', 2, 'JUDGE', '@Transactional 注解的方法发生任何异常都会默认回滚。', '["正确","错误"]', 'B', '默认仅对 RuntimeException 与 Error 回滚，受检异常需配置 rollbackFor。', NULL, 'Java', 4, 'Spring', 2, 'NORMAL', 2, NOW(), NOW()),
('PRIVATE', 2, 'SINGLE', 'C++ 中哪个智能指针独占所有权、不可复制？', '["shared_ptr","weak_ptr","unique_ptr","auto_ptr"]', 'C', 'unique_ptr 独占所有权，仅可移动；auto_ptr 已废弃。', NULL, 'C++', 3, '内存管理', 3, 'NORMAL', 2, NOW(), NOW()),
('PRIVATE', 2, 'ESSAY', '简述 Spring IOC 与 DI 的概念及好处。', NULL, 'IOC 控制反转：对象创建与依赖管理交给容器；DI 依赖注入是其实现方式，通过构造器/Setter/字段注入依赖。好处：解耦、易测试、便于替换实现与统一管理生命周期。', 'IOC 定义；DI 实现方式；解耦与可测试性。', '1.IOC：控制权交给容器；2.DI：构造器/Setter/字段注入任一说明；3.好处：解耦、可测试、生命周期管理；4.结合 Bean 装配说明。', 'Java', 3, 'Spring', 10, 'NORMAL', 2, NOW(), NOW());

-- ---------------- 数据部私有题库 ----------------
INSERT INTO question (bank_type, dept_id, type, content, options_json, answer, analysis, score_points, skill, difficulty, knowledge_point, default_score, status, creator_id, created_at, updated_at) VALUES
('PRIVATE', 3, 'SINGLE', 'MySQL 8.0 中用于排名分析的窗口函数是？', '["RANK()","GROUP()","ORDER()","TOP()"]', 'A', 'RANK/ROW_NUMBER/DENSE_RANK 均为窗口函数。', NULL, '数据库', 3, 'SQL', 3, 'NORMAL', 3, NOW(), NOW()),
('PRIVATE', 3, 'MULTI', '下列属于 NoSQL 数据库的有？', '["Redis","MongoDB","MySQL","HBase"]', 'ABD', 'MySQL 是关系型数据库。', NULL, '数据库', 2, 'NoSQL', 4, 'NORMAL', 3, NOW(), NOW()),
('PRIVATE', 3, 'ESSAY', '列举常见的 SQL 优化手段。', NULL, '避免 SELECT *；为查询条件建合适索引；避免索引失效（函数运算、隐式转换、左模糊）；大分页用延迟关联；用 EXPLAIN 分析执行计划；合理拆分大事务与慢查询。', '索引优化；避免索引失效；执行计划分析；写法优化。', '1.索引：合适索引与覆盖索引；2.避免索引失效场景任一；3.EXPLAIN 执行计划分析；4.写法：避免 SELECT *、大分页优化等任一。', '数据库', 4, 'SQL优化', 10, 'NORMAL', 3, NOW(), NOW());