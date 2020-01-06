package firestream.chat.chat;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import firestream.chat.firebase.rx.DisposableMap;
import firestream.chat.interfaces.IAbstractChat;
import firestream.chat.namespace.Fire;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import firestream.chat.Config;
import firestream.chat.events.ListEvent;
import firestream.chat.firebase.service.Path;
import firestream.chat.message.DeliveryReceipt;
import firestream.chat.message.Invitation;
import firestream.chat.message.Message;
import firestream.chat.message.Presence;
import firestream.chat.message.Sendable;
import firestream.chat.message.TypingState;

import firestream.chat.types.SendableType;

/**
 * This class handles common elements of a conversation bit it 1-to-1 or group.
 * Mainly sending and receiving messages.
 */
public abstract class AbstractChat implements Consumer<Throwable>, IAbstractChat {

    /**
     * Store the disposables so we can dispose of all of them when the user logs out
     */
    protected DisposableMap dm = new DisposableMap();

    /**
     * Event events
     */
    protected Events events = new Events();

    /**
     * A list of all sendables received
     */
    protected ArrayList<Sendable> sendables = new ArrayList<>();

    /**
     * Current configuration
     */
    protected Config config = new Config();

    /**
     * Error handler method so we can redirect all errors to the error events
     * @param throwable - the events error
     * @throws Exception
     */
    @Override
    public void accept(Throwable throwable) throws Exception {
        events.errors.onError(throwable);
    }

    /**
     * Start listening to the current errorMessage reference and retrieve all messages
     * @return a events of errorMessage results
     */
    protected Observable<Sendable> messagesOn() {
        return messagesOn(null);
    }

    /**
     * Start listening to the current errorMessage reference and pass the messages to the events
     * @param newerThan only listen for messages after this date
     * @return a events of errorMessage results
     */
    protected Observable<Sendable> messagesOn(Date newerThan) {
        return Fire.Stream.getFirebaseService().core.messagesOn(messagesPath(), newerThan, config.messageHistoryLimit).doOnNext(sendable -> {
            if (sendable != null) {
                getSendableEvents().getSendables().onNext(sendable);
                sendables.add(sendable);
            }
        }).doOnError(throwable -> {
            events.publishThrowable().onNext(throwable);
        }).subscribeOn(Schedulers.single()).observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Get a updateBatch of messages once
     * @param fromDate get messages from this date
     * @param toDate get messages until this date
     * @param limit limit the maximum number of messages
     * @return a events of errorMessage results
     */
    protected Single<List<Sendable>> loadMoreMessages(@Nullable Date fromDate, @Nullable Date toDate, @Nullable Integer limit) {
        return Fire.Stream.getFirebaseService().core
                .loadMoreMessages(messagesPath(), fromDate, toDate, limit)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<List<Sendable>> loadMoreMessages(Date fromDate, Date toDate) {
        return loadMoreMessages(fromDate, toDate, null);
    }

    public Single<List<Sendable>> loadMoreMessagesFrom(Date fromDate, Integer limit) {
        return loadMoreMessages(fromDate, null, limit);
    }

    public Single<List<Sendable>> loadMoreMessagesTo(Date toDate, Integer limit) {
        return loadMoreMessages(null, toDate, limit);
    }

    /**
     * This method gets the date of the last delivery receipt that we sent - i.e. the
     * last errorMessage WE received.
     * @return single date
     */
    protected Single<Date> dateOfLastDeliveryReceipt() {
        return Fire.Stream.getFirebaseService().core
                .dateOfLastSentMessage(messagesPath())
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Listen for changes in the value of a list reference
     * @param path to listen to
     * @return events of list events
     */
    protected Observable<ListEvent> listChangeOn(Path path) {
        return Fire.Stream.getFirebaseService().core
                .listChangeOn(path)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Completable send(Path messagesPath, Sendable sendable) {
        return send(messagesPath, sendable, null);
    }

        /**
         * Send a errorMessage to a messages ref
         * @param messagesPath
         * @param sendable item to be sent
         * @param newId the ID of the new errorMessage
         * @return single containing errorMessage id
         */
    public Completable send(Path messagesPath, Sendable sendable, @Nullable Consumer<String> newId) {
        return Fire.Stream.getFirebaseService().core
                .send(messagesPath, sendable, newId)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Delete a sendable from our queue
     * @param messagesPath
     * @return completion
     */
    protected Completable deleteSendable (Path messagesPath) {
        return Fire.Stream.getFirebaseService().core
                .deleteSendable(messagesPath)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Remove a user from a reference
     * @param path for users
     * @param user to remove
     * @return completion
     */
    protected Completable removeUser(Path path, User user) {
        return removeUsers(path, user);
    }

    /**
     * Remove users from a reference
     * @param path for users
     * @param users to remove
     * @return completion
     */
    protected Completable removeUsers(Path path, User... users) {
        return removeUsers(path, Arrays.asList(users));
    }

    /**
     * Remove users from a reference
     * @param path for users
     * @param users to remove
     * @return completion
     */
    protected Completable removeUsers(Path path, List<User> users) {
        return Fire.Stream.getFirebaseService().core
                .removeUsers(path, users)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Add a user to a reference
     * @param path for users
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param user to add
     * @return completion
     */
    protected Completable addUser(Path path, User.DataProvider dataProvider, User user) {
        return addUsers(path, dataProvider, user);
    }

    /**
     * Add users to a reference
     * @param path for users
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param users to add
     * @return completion
     */
    public Completable addUsers(Path path, User.DataProvider dataProvider, User... users) {
        return addUsers(path, dataProvider, Arrays.asList(users));
    }

    /**
     * Add users to a reference
     * @param path
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param users to add
     * @return completion
     */
    public Completable addUsers(Path path, User.DataProvider dataProvider, List<User> users) {
        return Fire.Stream.getFirebaseService().core
                .addUsers(path, dataProvider, users)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Updates a user for a reference
     * @param path for users
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param user to update
     * @return completion
     */
    public Completable updateUser(Path path, User.DataProvider dataProvider, User user) {
        return updateUsers(path, dataProvider, user);
    }

    /**
     * Update users for a reference
     * @param path for users
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param users to update
     * @return completion
     */
    public Completable updateUsers(Path path, User.DataProvider dataProvider, User... users) {
        return updateUsers(path, dataProvider, Arrays.asList(users));
    }

    /**
     * Update users for a reference
     * @param path for users
     * @param dataProvider a callback to extract the data to add from the user
     *                     this allows us to use one method to write to multiple different places
     * @param users to update
     * @return completion
     */
    public Completable updateUsers(Path path, User.DataProvider dataProvider, List<User> users) {
        return Fire.Stream.getFirebaseService().core
                .updateUsers(path, dataProvider, users)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void connect() throws Exception {
        dm.add(dateOfLastDeliveryReceipt()
                .flatMapObservable(this::messagesOn)
                .subscribe(this::passMessageResultToStream, this));
    }

    @Override
    public void disconnect () {
        dm.dispose();
    }

    /**
     * Convenience method to cast sendables and send them to the correct events
     * @param sendable the base sendable
     */
    protected void passMessageResultToStream(Sendable sendable) {

        System.out.println("Sendable: " + sendable.type + " " + sendable.id + ", date: " + sendable.date.getTime());

        if (sendable.type.equals(SendableType.Message)) {
            events.getMessages().onNext(Message.fromSendable(sendable));
        }
        if (sendable.type.equals(SendableType.DeliveryReceipt)) {
            events.getDeliveryReceipts().onNext(DeliveryReceipt.fromSendable(sendable));
        }
        if (sendable.type.equals(SendableType.TypingState)) {
            events.getTypingStates().onNext(TypingState.fromSendable(sendable));
        }
        if (sendable.type.equals(SendableType.Invitation)) {
            events.getInvitations().onNext(Invitation.fromSendable(sendable));
        }
        if (sendable.type.equals(SendableType.Presence)) {
            events.getPresences().onNext(Presence.fromSendable(sendable));
        }
    }

    @Override
    public ArrayList<Sendable> getSendables() {
        return sendables;
    }

    @Override
    public ArrayList<Sendable> getSendables(SendableType type) {
        return Lists.newArrayList(Collections2.filter(sendables, input -> input != null && input.type.equals(type.get())));
    }

    /**
     * returns the events object which exposes the different sendable streams
     * @return events
     */
    public Events getSendableEvents() {
        return events;
    }

    /**
     * Overridable messages reference
     * @return Firestore messages reference
     */
    protected abstract Path messagesPath();

    @Override
    public DisposableMap getDisposableMap() {
        return dm;
    }

    @Override
    public void manage(Disposable disposable) {
        getDisposableMap().add(disposable);
    }

    public abstract Completable markRead(Message message);
    public abstract Completable markReceived(Message message);

}