# PTA 函数题蓝本（从实验1-1开始）

采集日期：2026-11-06。共30条目录；30题均已读取；第4题初次跳转登录，后续在浏览器中成功读取。这里保留题意摘要、接口和适配评审，不将目录收录等同于题目已导入或可评测。完整目录见 [PTA_CATALOG.md](PTA_CATALOG.md)。

原题为 C++ 函数题。以下测试建议和 C17 适配方向是本项目设计；未取得 PTA 参考答案或隐藏输入输出。公开裁判程序不是参考答案。支持 C++17 编译不意味着教材依赖类已自动齐全。

## 逐题记录

### 1. [实验1-1 有序数组的插入（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049728)

- 题意：在容量有限的递减整数序列中插入新值；重复或已满时失败，原序列保持不变。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例2组。
- 教学点：数组移动、容量与重复检测。
- 建议测试：空数组、首中尾插入、满容量、重复和负数。
- 适配待办：明确容量范围；C17 改为结构体指针，CPP17 可保留引用。

公开接口（引用）：
```cpp
bool DecrSeqInsert(ArrNode& array, int x);
```

### 2. [实验2-1 求链式线性表的倒数第 m 项（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049729)

- 题意：查询带空头结点单链表的倒数第 m 个值，不能修改链表；不存在时返回错误标志。
- 资源：20分；10000 ms；512 MB；代码100 KB；去重公开样例1组。
- 教学点：双指针、只读遍历、位置边界。
- 建议测试：空表、m=1、m=长度、m 超长。
- 适配待办：补全链表基础类和错误值定义；C17 用结构体与状态返回值。

公开接口（引用）：
```cpp
template <typename ElemType>
ElemType LinkedList<ElemType>::Find(int m)
```

### 3. [实验2-3 线性表元素的区间删除（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049730)

- 题意：从顺序表删去值落在开区间内的元素，保留元素的相对次序。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：稳定压缩、开区间边界。
- 建议测试：全删、不删、端点值、连续与交错删除。
- 适配待办：明确 min>=max 的行为和 last_ 的含义。

公开接口（引用）：
```cpp
template <typename ElemType>
void List<ElemType>::Delete(ElemType minValue, ElemType maxValue)
```

### 4. [实验2-4 单链表分段逆转（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049731)

- 题意：对带头结点的单链表按每 k 个节点分组反转，末尾不足一组的节点保持原序。
- 资源：25分；400 ms；64 MB；代码16 KB；公开样例1组。
- 教学点：指针反转、分组边界、尾节点维护。
- 建议测试：k=1、恰好整组、尾部不足k、k超过长度、空表。
- 适配待办：接口请求曾失败；后续浏览器已读取题面，公开样例确认末尾不足一组保持原序。补基础链表实现及k的有效范围。

公开接口（引用）：
```cpp
template <typename ElemType>
void LinkedList<ElemType>::K_Reverse(int k)
```

### 5. [实验2-5 约瑟夫问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049732)

- 题意：按给定步长环形报数，返回所有人的出列次序，结果使用带头结点链表保存。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：循环链表、删除与状态更新。
- 建议测试：单人、步长1、步长超过人数。
- 适配待办：独立提供循环链表基础实现，明确参数范围。

公开接口（引用）：
```cpp
template<typename T>
LinkedList<T> JosephusProblem(int n, int m)
```

### 6. [实验2-6 判断两个广义表是否相等（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049733)

- 题意：比较两个广义表的节点类型、数据及嵌套结构是否一致。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例2组。
- 教学点：递归、原子与子表区分。
- 建议测试：空表、嵌套空表、类型不同、数据不同。
- 适配待办：公开接口与类内声明对参数 const 存在差异，发布前统一。

公开接口（引用）：
```cpp
bool GList::IsEqualRecursive(GListNode* glist1, GListNode* glist2) const
```

### 7. [实验3-1 在一个数组中实现两个栈（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049734)

- 题意：两个栈共享同一数组，在合计容量耗尽前均可继续入栈，并支持常数时间入栈和弹栈。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：共享空间、双栈边界。
- 建议测试：两栈交替操作、一边占满、空栈弹出。
- 适配待办：定义错误返回值及栈编号合法范围。

公开接口（引用）：
```cpp
void Stack::InitStack(int n);
bool Stack::Push(int tag, SElemSet x);
bool Stack::IsEmpty(int tag) const;
SElemSet Stack::TopPop(int tag);
```

### 8. [实验3-5 双端队列（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049735)

- 题意：用循环数组实现双端队列的两端插入与删除，区分空队列和满队列。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：环形下标、保留空位。
- 建议测试：空/满、跨尾回绕、两端交错操作。
- 适配待办：为公开接口补分号并明确容量和错误值。

公开接口（引用）：
```cpp
bool Deque::EnQueue(int x)
int Deque::DeQueue()
bool Deque::Inject(int x) 
int Deque::Eject()
```

### 9. [实验5-2 树的宽度（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049736)

- 题意：根据含空节点标记的层序数组，求各层实际节点数的最大值；空树结果为0。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：树的层次与表示规则。
- 建议测试：空树、单节点、链状树、稀疏层。
- 适配待办：明确数组是完整位置编码还是紧凑层序编码，不能混用。

公开接口（引用）：
```cpp
int Width(TElemSet levelorder[], int n);
```

### 10. [实验5-3 二叉树直径（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049737)

- 题意：计算二叉树高度及最长节点间路径；直径按经过的边数计。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：递归、子树汇总。
- 建议测试：空树、单节点、最长路径不经过根。
- 适配待办：独立提供建树和销毁程序，明确高度基准。

公开接口（引用）：
```cpp
int BinaryTree::Height(BinaryTreeNode* node);
int BinaryTree::Diameter(BinaryTreeNode* node);
```

### 11. [实验6-5 对顶堆维护中位数（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049738)

- 题意：支持集合插入和中位数查询；元素数为偶数时取两个中间值的平均。
- 资源：30分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：对顶堆、平衡与数值范围。
- 建议测试：重复、奇偶数量、负数、大整数均值。
- 适配待办：依赖教材 BinaryHeap，需自行补全基础类并定义空集查询。

公开接口（引用）：
```cpp
void Set::Insert(Set::ElemSet x)
double Set::GetMedia()
```

### 12. [实验7-4 是否有回路（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049739)

- 题意：判断邻接表表示的有向图是否无环，返回真代表无环。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：图遍历、拓扑关系。
- 建议测试：自环、孤立点、非连通分量中的环。
- 适配待办：注意题名问有回路而接口返回无环，避免布尔值反转。

公开接口（引用）：
```cpp
bool IsAcyclic(const LGraph& graph)
```

### 13. [实验10-1 分类排序（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049740)

- 题意：将字符数组按大写字母、数字、小写字母分成三个连续区域，区内次序不限。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：多路划分。
- 建议测试：缺某类、全同类、已分区、逆序。
- 适配待办：多解题：裁判需校验分区和元素多重集，不能 EXACT 对照单一排列。

公开接口（引用）：
```cpp
void Partition(vector<ElemSet>& a, int n);
```

### 14. [实验15-1~2 0-1背包问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049741)

- 题意：在承重约束下选择不可拆分物品，返回最大总价值并填写选择向量。
- 资源：30分；400 ms；512 MB；代码16 KB；去重公开样例1组。
- 教学点：搜索剪枝、分支限界。
- 建议测试：恰好装满、全超重、多个最优解、数值超过32位。
- 适配待办：总重量/价值可达10^10；需要64位类型和选择方案合法性校验。

公开接口（引用）：
```cpp
ElemSet Knapsack01(ElemSet W, Objects s, int *x, int n);
```

### 15. [练习1-1 二分查找（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049742)

- 题意：在升序序列中搜索目标，存在时返回下标，否则给出未找到标志，并分析复杂度。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例2组。
- 教学点：二分查找、区间收缩。
- 建议测试：空序列、首尾、未命中、单元素。
- 适配待办：明确重复键时的返回约定、失败标志和下标起点。

公开接口（引用）：
```cpp
Position BinarySearch(const vector<ElemSet>& array, ElemSet x);
```

### 16. [练习2-1 带空头结点的单链表操作（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049743)

- 题意：为带空头结点单链表实现按1起始位置插入和删除，非法位置输出错误提示。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例3组。
- 教学点：指针连接、长度维护。
- 建议测试：空表插入、首尾删除、0及越界位置。
- 适配待办：补完整基础类，规定失败后表保持不变。

公开接口（引用）：
```cpp
template <typename ElemType>
void LinkedList<ElemType>::Insert(int i, ElemType x)    
template <typename ElemType>
void LinkedList<ElemType>::Remove(int i)
```

### 17. [练习3-1 仅有头指针的队列（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049744)

- 题意：循环数组队列只保存头位置与元素计数，实现判空、入队和出队。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：计数式环形队列。
- 建议测试：空/满、回绕、入出交错。
- 适配待办：公开 IsEmpty 签名与类内 const 不一致，需统一。

公开接口（引用）：
```cpp
template <typename QElemSet>
bool Queue<QElemSet>::IsEmpty();
template <typename QElemSet>
void Queue<QElemSet>::EnQueue(QElemSet x);
template <typename QElemSet>
void Queue<QElemSet>::DeQueue();
```

### 18. [练习5-1 顺序存储的二叉树的遍历（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049745)

- 题意：为顺序存储的字符二叉树实现非递归前、中、后序遍历。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：显式栈、遍历次序。
- 建议测试：空节点、单支树、内部空位、完整树。
- 适配待办：明确数组下标与子节点位置规则；输出字符不带空格。

公开接口（引用）：
```cpp
void PreOrder(BinaryTree tree);
void InOrder(BinaryTree tree);
void PostOrder(BinaryTree tree);
```

### 19. [练习5-4 转换树的表示法（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049746)

- 题意：将父节点索引数组转换为孩子—兄弟链表表示，兄弟按原节点索引递增排列。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：多种树表示、构造与链接。
- 建议测试：根不在首位、多个孩子、链状树。
- 适配待办：补全 BinaryTree 类型别名，独立验证结构及兄弟顺序。

公开接口（引用）：
```cpp
template <typename TElemSet>
BinaryTree<TElemSet> ConvertTree(const Tree<TElemSet>& tree, int n);
```

### 20. [练习7-2 判断有向图中两点是否连通（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049747)

- 题意：判断有向图中从指定起点到终点是否存在路径。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：搜索、方向性、访问标记。
- 建议测试：反向不可达、环、孤立点、起终点相同。
- 适配待办：公开泛型名 VerInfo/VertInfo 等存在不一致，需修正后构建。

公开接口（引用）：
```cpp
template <typename GElemSet,typename VerInfo>
bool ExistPath(const LGraph<GElemSet,VerInfo>& graph, Vertex u, Vertex v)
```

### 21. [练习7-3 判断广度优先遍历序列（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049748)

- 题意：判断给定顶点排列是否可能是无向图的一次广度优先遍历结果。
- 资源：25分；400 ms；1024 MB；代码16 KB；去重公开样例1组。
- 教学点：BFS层次、同层访问次序。
- 建议测试：重复或漏点、跨层乱序、多种合法同层次序。
- 适配待办：明确不连通图与起点规则，补齐矩阵图类。

公开接口（引用）：
```cpp
template <typename GElemSet, typename VerInfo> 
bool IsBFS(const MGraph<GElemSet,VerInfo> &graph, const std::vector<Vertex> &ver)
```

### 22. [练习10-2 正负数分类（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049749)

- 题意：原地重排整数数组，使所有负数位于非负数之前，分区内顺序不限。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：双指针、零的归属。
- 建议测试：全正、全负、零、边界整数。
- 适配待办：多解题，裁判必须检查分区与元素守恒；源文105可能排版丢失幂次，待核对上限。

公开接口（引用）：
```cpp
void Partition( ElemSet *a, int n );
```

### 23. [练习11-2 是否二叉查找树（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049750)

- 题意：判断给定二叉树是否满足二叉搜索树的顺序关系。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例2组。
- 教学点：全局上下界或中序性质。
- 建议测试：局部满足而祖先约束违反、空树、重复键。
- 适配待办：明确重复键政策及空树返回值，不能仅比较父子。

公开接口（引用）：
```cpp
template <typename TElemSet>
bool IsBST(BinaryTree<TElemSet> tree);
```

### 24. [练习14-1 B+树的查找（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049751)

- 题意：在叶子保存记录、内部键复制子树最大键的 B+ 树中查找目标叶子和记录。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：B+树索引、叶边界。
- 建议测试：最小/最大键、缺失键、分支边界。
- 适配待办：独立补树基础实现；不存在键的候选叶位置按原题右侧优先约定。

公开接口（引用）：
```cpp
template <typename ElemSet>
typename BTree<ElemSet>::Position BTree<ElemSet>::FindLeaf(ElemSet key)
template <typename ElemSet>
TElemSet<ElemSet>* BTree<ElemSet>::SearchBTree(ElemSet key)
```

### 25. [练习15-1 分书问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049752)

- 题意：根据人对书的喜好矩阵，为每人分配一本不同的喜欢的书，按字典序输出全部方案。
- 资源：20分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：回溯、约束匹配。
- 建议测试：无解、唯一解、多解、书数多于人数。
- 适配待办：人数和书数2至8；明确状态编号、Print 和无解输出。

公开接口（引用）：
```cpp
void BookAssignmentBF(vector<vector<int>>& table, int n, int m);
```

### 26. [练习15-2 n皇后问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049753)

- 题意：寻找 n 皇后的合法摆放，要求输出状态向量中字典序最大的一组。
- 资源：25分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：回溯、列及对角线约束。
- 建议测试：n=4、多个解、较大规模。
- 适配待办：原范围4至20，不能仅输出任意解或将枚举所有解视为要求。

公开接口（引用）：
```cpp
void Queens( int n );
```

### 27. [练习15-3 旅行商问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049754)

- 题意：求从起点出发、每城访问一次并返回起点的最低旅行费用；无可行回路返回0。
- 资源：30分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：分支限界、Hamilton回路。
- 建议测试：无回路、唯一回路、不同最优路线。
- 适配待办：顶点数最多10，缺边标记10^9；需明确累加类型与单顶点行为。

公开接口（引用）：
```cpp
int TSP(vector<vector<int>>& graph );
```

### 28. [练习15-4 最长公共子序列（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049755)

- 题意：输出两个字符串的一条最长公共子序列，并返回其长度；最优序列可不唯一。
- 资源：30分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：动态规划、路径恢复。
- 建议测试：空串、无共同字符、重复字符、多最优解。
- 适配待办：多解输出需要检查子序列性质和最优长度，不能单一文本对照。

公开接口（引用）：
```cpp
int LCS(const std::string& x,const std::string& y);
```

### 29. [练习15-5 带权的活动安排问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049756)

- 题意：在单间活动室选取互不冲突的活动，使总权重最大；前项结束等于后项开始允许衔接。
- 资源：30分；400 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：带权区间调度。
- 建议测试：衔接、嵌套、重叠、非贪心最优。
- 适配待办：输入按结束时间排序且有0号哨兵，明确 n 是否含哨兵。

公开接口（引用）：
```cpp
int WeightedActivitySelection(vector<ActivityNode>& a, int n);
```

### 30. [练习15-6 教室安排问题（C++版）](https://pintia.cn/problem-sets/1927228151378894848/exam/problems/type/6?problemSetProblemId=1927228279999049757)

- 题意：安排所有活动所需的最少教室数，同一教室的活动不能时间重叠。
- 资源：25分；1000 ms；64 MB；代码16 KB；去重公开样例1组。
- 教学点：区间并发、资源分配。
- 建议测试：完全重叠、首尾衔接、嵌套、多活动同起点。
- 适配待办：输入按开始时间排序，有0号哨兵；明确结束与开始同时发生的处理。

公开接口（引用）：
```cpp
int ActivityScheduling(vector<ActivityNode>& a, int n);
```
