package shirlin.ai.types.design.link.model2.chain;

public class LinkedList<E> implements ILink<E> {
    private final String name;
    transient int size = 0;
    transient Node<E> first;
    transient Node<E> last;

    public LinkedList(String name) {
        this.name = name;
    }

    void linkFirst(E e) {
        Node<E> f = this.first;
        Node<E> newNode = new Node<E>((Node) null, e, f);
        this.first = newNode;
        if (f == null) {
            this.last = newNode;
        } else {
            f.prev = newNode;
        }

        ++this.size;
    }

    void linkLast(E e) {
        Node<E> l = this.last;
        Node<E> newNode = new Node<E>(l, e, (Node) null);
        this.last = newNode;
        if (l == null) {
            this.first = newNode;
        } else {
            l.next = newNode;
        }

        ++this.size;
    }

    public boolean add(E e) {
        this.linkLast(e);
        return true;
    }

    public boolean addFirst(E e) {
        this.linkFirst(e);
        return true;
    }

    public boolean addLast(E e) {
        this.linkLast(e);
        return true;
    }

    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = this.first; x != null; x = x.next) {
                if (x.item == null) {
                    this.unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = this.first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    this.unlink(x);
                    return true;
                }
            }
        }

        return false;
    }

    E unlink(Node<E> x) {
        E element = x.item;
        Node<E> next = x.next;
        Node<E> prev = x.prev;
        if (prev == null) {
            this.first = next;
        } else {
            prev.next = next;
            x.prev = null;
        }

        if (next == null) {
            this.last = prev;
        } else {
            next.prev = prev;
            x.next = null;
        }

        x.item = null;
        --this.size;
        return element;
    }

    public E get(int index) {
        return this.node(index).item;
    }

    Node<E> node(int index) {
        if (index < this.size >> 1) {
            Node<E> x = this.first;

            for (int i = 0; i < index; ++i) {
                x = x.next;
            }

            return x;
        } else {
            Node<E> x = this.last;

            for (int i = this.size - 1; i > index; --i) {
                x = x.prev;
            }

            return x;
        }
    }

    public void printLinkList() {
        if (this.size == 0) {
            System.out.println("链表为空");
        } else {
            Node<E> temp = this.first;
            System.out.print("目前的列表，头节点：" + this.first.item + " 尾节点：" + this.last.item + " 整体：");

            while (temp != null) {
                System.out.print(temp.item + "，");
                temp = temp.next;
            }

            System.out.println();
        }

    }

    public String getName() {
        return this.name;
    }

    protected static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        public Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
}