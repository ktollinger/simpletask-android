package nl.mpcjanssen.simpletask.sort;

import nl.mpcjanssen.simpletask.task.Task;

public class FileOrderComparator extends ReversableComparator {

    public FileOrderComparator(boolean reverse) {
        super(reverse);
    }

    @Override
    public int unreversedCompare(Task a, Task b) {
        return (int)(a.getLine() - b.getLine());
    }
}
