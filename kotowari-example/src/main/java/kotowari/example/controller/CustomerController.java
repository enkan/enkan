package kotowari.example.controller;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import enkan.component.doma2.DomaProvider;
import enkan.web.data.HttpResponse;
import kotowari.component.TemplateEngine;
import kotowari.example.dao.CustomerDao;
import kotowari.example.entity.Customer;
import kotowari.example.form.CustomerForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

import static enkan.web.util.HttpResponseUtils.RedirectStatusCode.SEE_OTHER;
import static kotowari.routing.UrlRewriter.redirect;


/**
 * CRUD example
 *
 * @author kawasima
 */
public class CustomerController {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerController.class);

    @Inject
    private TemplateEngine<?> templateEngine;

    @Inject
    private DomaProvider daoProvider;

    @Inject
    private BeansConverter beans;

    public HttpResponse index() {
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        List<Customer> customers = customerDao.selectAll();
        return templateEngine.render("customer/list",
                "customers", customers);
    }

    public List<Customer> list() {
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        return customerDao.selectAll();
    }

    public Boolean validate(CustomerForm customer) {
        LOG.info(customer.toString());
        return true;
    }

    public HttpResponse show(Parameters params) {
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        Customer customer = customerDao.selectById(params.getLong("id"));
        return templateEngine.render("customer/show", "customer", customer);
    }

    public HttpResponse newForm() {
        return templateEngine.render("customer/new",
                "customer", new CustomerForm());
    }

    @Transactional
    public HttpResponse create(CustomerForm form) {
        if (form.hasErrors()) {
            return templateEngine.render("customer/new", "customer", form);
        }
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        Customer customer = beans.createFrom(form, Customer.class);
        customerDao.insert(customer);
        return redirect(getClass(), "index", SEE_OTHER);
    }

    public HttpResponse edit(Parameters params) {
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        Customer customer = customerDao.selectById(params.getLong("id"));
        CustomerForm form = beans.createFrom(customer, CustomerForm.class);
        return templateEngine.render("customer/edit",
                "id", params.getLong("id"),
                "customer", form);
    }

    @Transactional
    public HttpResponse update(Parameters params, CustomerForm form) {
        if (form.hasErrors()) {
            return templateEngine.render("customer/edit",
                    "id", params.getLong("id"),
                    "customer", form);
        }
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        Customer customer = customerDao.selectById(params.getLong("id"));
        beans.copy(form, customer);
        customerDao.update(customer);
        return redirect(getClass(), "show?id=" + customer.getId(), SEE_OTHER);
    }

    @Transactional
    public HttpResponse delete(Parameters params) {
        CustomerDao customerDao = daoProvider.getDao(CustomerDao.class);
        Customer customer = customerDao.selectById(params.getLong("id"));
        customerDao.delete(customer);

        return redirect(getClass(), "index", SEE_OTHER);
    }

}
