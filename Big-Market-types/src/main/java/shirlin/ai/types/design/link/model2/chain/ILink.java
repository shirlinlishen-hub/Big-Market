package shirlin.ai.types.design.link.model2.chain;

public interface ILink<E> {
    boolean add(E var1);

    boolean addFirst(E var1);

    boolean addLast(E var1);

    boolean remove(Object var1);

    E get(int var1);

    void printLinkList();
}