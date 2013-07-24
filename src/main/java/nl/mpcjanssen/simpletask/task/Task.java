/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).
 *
 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 *
 * LICENSE:
 *
 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.
 *
 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Todo.txt contributors <todotxt@yahoogroups.com>
 * @license http://www.gnu.org/licenses/gpl.html
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 */
package nl.mpcjanssen.simpletask.task;

import nl.mpcjanssen.simpletask.Constants;
import nl.mpcjanssen.simpletask.util.RelativeDate;
import nl.mpcjanssen.simpletask.util.Strings;

import java.io.Serializable;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SuppressWarnings("serial")
public class Task implements Serializable, Comparable<Task> {

    private static final long serialVersionUID = 1L;

    private static final Pattern TAG_PATTERN = Pattern
            .compile("^\\S*[\\p{javaLetterOrDigit}_]$");
    private static final Pattern DUE_PATTERN =  Pattern
            .compile("\\sdue:(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern THRESHOLD_PATTERN =  Pattern
            .compile("\\st:(\\d{4}-\\d{2}-\\d{2})");

    private static final String COMPLETED = "x ";
    private static final String DATE_REGEXP = "(\\d{4}-\\d{2}-\\d{2})";

    private long id;
    private Priority priority;
    private String text;
    private String prependedDate;
    private String relativeAge = "";
    private List<String> contexts;
    private List<String> projects;
    private List<String> phoneNumbers;
    private List<String> mailAddresses;
    private List<URL> links;
    private Date dueDate;
    private SimpleDateFormat formatter;
    private Date thresholdDate;
    private String rawText;


    public static boolean validTag(String tag) {
        return TAG_PATTERN.matcher(tag).find();
    }

    public Task(long id, String rawText, Date defaultPrependedDate) {
        this.id = id;
        this.init(rawText, defaultPrependedDate);
    }

    public Task(long id, String rawText) {
        this(id, rawText, null);
    }

    public void update(String rawText) {
        this.init(rawText, null);
    }

    public void init(String rawText, Date defaultPrependedDate) {
        TextSplitter splitter = TextSplitter.getInstance();
        TextSplitter.SplitResult splitResult = splitter.split(rawText);
        this.priority = splitResult.priority;

        // Text without prepended date
        this.text = splitResult.text;
        this.prependedDate = splitResult.prependedDate;
        this.rawText = rawText;

        this.phoneNumbers = PhoneNumberParser.getInstance().parse(text);
        this.mailAddresses = MailAddressParser.getInstance().parse(text);
        this.links = LinkParser.getInstance().parse(text);
        this.contexts = ContextParser.getInstance().parse(text);
        this.projects = ProjectParser.getInstance().parse(text);
        this.formatter = new SimpleDateFormat(Constants.DATE_FORMAT, Locale.US);
        if (defaultPrependedDate != null
                && Strings.isEmptyOrNull(this.prependedDate)) {
            this.prependedDate = formatter.format(defaultPrependedDate);
            this.rawText = prependedDate + " " + rawText;
        }

        if (!Strings.isEmptyOrNull(this.prependedDate)) {
            try {
                Date d = formatter.parse(this.prependedDate);
                this.relativeAge = RelativeDate.getRelativeDate(d);
            } catch (ParseException e) {
                // e.printStackTrace();
            }
        }

        Matcher matcher = DUE_PATTERN.matcher(this.text);
        if (matcher.find()) {
            try {
                this.dueDate = formatter.parse(matcher.group(1));
            } catch (ParseException e) {
                this.dueDate = null;
            }
        }

        matcher = THRESHOLD_PATTERN.matcher(this.text);
        if (matcher.find()) {
            try {
                this.thresholdDate = formatter.parse(matcher.group(1));
            } catch (ParseException e) {
                this.thresholdDate = null;
            }
        }
    }

    public Date getDueDate() {
        return this.dueDate;
    }

    public Date getThresholdDate() {
        return this.thresholdDate;
    }

    public String getText() {
        return text;
    }

    public long getId() {
        return id;
    }

    public void setPriority(Priority newPriority) {
        if (newPriority==Priority.NONE) {
            rawText = rawText.replaceFirst("^\\([A-Z]\\)\\s", "");
        } else if (this.priority == Priority.NONE) {
            rawText = newPriority.inFileFormat() + " " + rawText;
        } else {
            rawText = rawText.replaceFirst("^\\([A-Z]\\)", newPriority.inFileFormat());
        }
        priority = newPriority;
        init(rawText,null);
    }

    public Priority getPriority() {
        return priority;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public List<String> getProjects() {
        return projects;
    }

    public String getPrependedDate() {
        return prependedDate;
    }

    public String getRelativeAge() {
        return relativeAge;
    }

    public boolean isCompleted() {
        return rawText.startsWith("x ");
    }

    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    public List<String> getMailAddresses() {
        return mailAddresses;
    }

    public List<URL> getLinks() {
        return links;
    }

    public void markComplete(Date date) {
        if (!isCompleted()) {
            String completionDate = formatter.format(date);
            setPriority(Priority.NONE);
            rawText = COMPLETED + completionDate + " " + rawText;
            init(rawText,null);
        }

    }

    public void markIncomplete() {
        if(isCompleted()) {
            rawText = rawText.replaceFirst("^"+COMPLETED, "");
            rawText = rawText.replaceFirst("^"  + DATE_REGEXP + "\\s","");
            init(rawText,null);
        }
    }

    public void delete() {
        this.update("");
    }

    public boolean inFuture() {
        if (this.getThresholdDate()==null) {
            return false;
        } else {
            Date thresholdDate = this.getThresholdDate();
            Date now = new Date();
            return thresholdDate.after(now);
        }
    }

    public String inFileFormat() {
        return rawText;
    }

    public void copyInto(Task destination) {
        destination.id = this.id;
        destination.init(this.inFileFormat(), null);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Task other = (Task) obj;
        if (id != other.id)
            return false;
        return (this.rawText.equals(other.rawText));
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id ^ (id >>> 32));
        result = prime * result + ((rawText == null) ? 0 : rawText.hashCode());
        return result;
    }

    public void initWithFilters(ArrayList<String> ctxts, ArrayList<String> pjs) {
        if ((ctxts != null) && (ctxts.size() == 1)) {
            contexts.clear();
            contexts.add(ctxts.get(0));
        }
        if ((pjs != null) && (pjs.size() == 1)) {
            projects.clear();
            projects.add(pjs.get(0));
        }
    }

    /**
     * @param another Task to compare this task to
     * @return comparison of the position of the tasks in the file
     */
    @Override
    public int compareTo(Task another) {
        return ((Long) this.getId()).compareTo(another.getId());
    }

	public void append(String string) {
		this.init(rawText + " " + string , null);
	}

    public void removeTag(String tag) {
        String newText = inFileFormat().replaceAll(Pattern.quote(tag), " ");
        newText = newText.trim();
        this.init(newText , null);
    }

    /* Adds the task to list Listname
    ** If the task is already on that list, it does nothing
     */
    public void addList(String listName) {
        if (!getContexts().contains(listName)) {
            append ("@" + listName);
        }
    }

    /* Tags the task with tag
    ** If the task already has te tag, it does nothing
    */
    public void addTag(String tag) {
        if (!getProjects().contains(tag)) {
            append ("+" + tag);
        }
    }

    public void deferDueDate(String deferString) {
        String taskContents = inFileFormat();
        if (dueDate!=null) {
            taskContents = taskContents.replaceFirst(DUE_PATTERN.pattern(), " due:" + deferString);
        } else {
            taskContents = taskContents + " due:" + deferString;
        }
        init(taskContents,null);
    }

    public void deferThresholdDate(String deferString) {
        String taskContents = inFileFormat();
        if (thresholdDate!=null) {
            taskContents = taskContents.replaceFirst(THRESHOLD_PATTERN.pattern(), " t:" + deferString);
        } else {
            taskContents = taskContents + " t:" + deferString;
        }
        init(taskContents,null);
    }

    public void deferToDate(boolean isThresholdDate, String deferString) {
        if (isThresholdDate) {
            deferThresholdDate(deferString);
        } else {
            deferDueDate(deferString);
        }
    }

    public void deferToDate(boolean isThresholdDate, Date deferDate) {
        String deferString = formatter.format(deferDate);
        deferToDate(isThresholdDate, deferString);
    }

    public String inScreenFormat() {
        String result = inFileFormat();
        if (!Strings.isEmptyOrNull(getPrependedDate())) {
            result = result.replaceFirst(getPrependedDate()+"\\s", "");
        }
        return result;
    }
}
